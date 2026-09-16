package com.example.saa.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * ============================================================================
 * 【知识点 11】并发工具调用（Concurrent Tool Calling）—— 同一个工具被并行调用
 * ============================================================================
 *
 * 什么时候会出现"同一个工具被并发调用"？
 *   1) 模型一次返回多个 tool_calls：用户问"同时查广州、上海、深圳的天气"，
 *      模型可能在一条响应里给出 3 个对 getWeather 的调用请求——同一个方法、3 份参数；
 *   2) 多个用户/多个请求同时进来：Tomcat 的工作线程池本身就是并发的，
 *      100 个用户同时在用，同一个 @Component 工具实例的同一个方法就有 100 个并发调用。
 *   两种情况殊途同归：你的工具方法跑在多线程环境里，必须按"线程安全"标准来写。
 *
 * 本类一鱼三吃，故意并排放了三组对照：
 *   A) 反例 unsafe*（故意不线程安全）vs 正例 safe*（线程安全）——
 *      在 /ai/tool-race 接口用线程池真实并发调用，看错误计数如何发生；
 *   B) 慢速工具 slowReport —— 在工具里记录起止时间戳与线程名，
 *      在 /ai/tool-parallel 接口观察"模型一次返回多个 tool_call 时到底怎么执行"；
 *   C) 幂等工具 submitOrder —— 演示"工具被重复调用"的防线（幂等设计）。
 *
 * 背景事实（以 Spring AI 1.1.2 源码为准，写注释不猜）：
 *   DefaultToolCallingManager 在一次响应包含多个 tool_call 时是【串行逐个执行】的
 *   （builder 也没有 parallel 相关选项）；并发场景真正常态的是上面第 2) 种——
 *   多个 HTTP 请求各自驱动各自的 tool 执行循环，落在同一个单例工具上。
 *   也就是说：即使框架是串行的，你的工具也必须线程安全。
 */
@Component
public class ConcurrencyDemoTools {

    // =========================================================================
    // A) 反例 vs 正例：计数器
    // =========================================================================

    /**
     * 【反例·故意不线程安全】普通 HashMap + 普通 long 自增。
     * 两个错误示范都在这里：
     *   - HashMap 并发 put 会丢数据甚至（JDK7）死循环；
     *   - unsafeTotal++ 不是原子操作（读-加-写三步），并发下丢更新（lost update）。
     * 千万不要在真实代码里这样写——保留它是为了让 /ai/tool-race 的结果"看得见"。
     */
    private final Map<String, Integer> unsafeCountMap = new HashMap<>();
    private long unsafeTotal = 0;

    /**
     * 【正例·线程安全】ConcurrentHashMap（分段/ CAS 并发容器）+ AtomicLong（原子自增）。
     * 进阶提示：纯计数场景用 LongAdder 更好——高并发下把一个数拆成多个槽分别累加、
     * 汇总时再求和，写竞争越小性能越好（本类 extra 里放了一个演示）。
     */
    private final Map<String, Integer> safeCountMap = new ConcurrentHashMap<>();
    private final AtomicLong safeTotal = new AtomicLong();

    /** LongAdder 演示：高并发计数的首选（仅统计总量，不需要按 key 细分时用） */
    private final LongAdder adderTotal = new LongAdder();

    /**
     * 【反例】自增计数（非线程安全）。"++"编译后是读、改、写三条指令，
     * 两个线程同时读到同一个旧值就会互相覆盖——这就是丢更新。
     */
    public long unsafeIncrement(String key) {
        unsafeCountMap.put(key, unsafeCountMap.getOrDefault(key, 0) + 1); // HashMap 并发写
        return ++unsafeTotal;                                             // 非原子自增
    }

    /** 【正例】自增计数（线程安全）：容器换并发容器，自增换原子类，一行都不多写 */
    public long safeIncrement(String key) {
        safeCountMap.merge(key, 1, Integer::sum); // ConcurrentHashMap.merge：CAS 保证原子合并
        adderTotal.increment();
        return safeTotal.incrementAndGet();
    }

    // =========================================================================
    // B) 慢速工具：观察多个 tool_call 的执行时序
    // =========================================================================

    /**
     * 工具执行时间线记录：每行格式 [开始时间] 线程名 工具(参数) 耗时ms。
     * 用 ConcurrentLinkedQueue（无锁并发队列）收集——如果用普通 ArrayList，
     * 收集日志这行代码自己就会成为并发 bug。
     */
    private final ConcurrentLinkedQueue<String> executionTimeline = new ConcurrentLinkedQueue<>();

    /**
     * 慢速工具：故意睡 1500ms 模拟真实工具的耗时（RPC / 慢 SQL / 三方 API）。
     * 慢是关键：只有足够慢，多个调用之间"时间上是否重叠"才能被时间戳分辨出来。
     */
    @Tool(description = "生成指定主题的市场简报，需要 1-2 秒。当用户要求生成报告/简报时调用。")
    public String slowReport(@ToolParam(description = "报告主题") String topic) {
        long start = System.nanoTime();
        String startTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        try {
            Thread.sleep(1500); // 模拟耗时 IO
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断标记，别吞掉中断
            return "报告生成被中断：" + topic;
        }
        long costMs = (System.nanoTime() - start) / 1_000_000;
        executionTimeline.add("[" + startTime + "] " + Thread.currentThread().getName()
                + " slowReport(" + topic + ") 耗时" + costMs + "ms");
        return "《" + topic + "市场简报》：需求平稳，竞争加剧，建议关注差异化服务（模拟数据）。";
    }

    /** 给 Controller 读取时间线（取走后清空，方便反复实验） */
    public List<String> drainTimeline() {
        List<String> lines = List.copyOf(executionTimeline);
        executionTimeline.clear();
        return lines;
    }

    // =========================================================================
    // C) 幂等工具：防"重复调用"的副作用
    // =========================================================================

    /**
     * 已处理订单号集合：幂等防重的最小实现（生产中通常用数据库唯一约束 / Redis SETNX）。
     * 为什么需要？模型可能因超时重试、或用户重复提问而重复触发写操作类工具；
     * 查询类工具天然幂等（查多少遍结果一样），【写操作类工具必须自己保证幂等】。
     */
    private final Set<String> processedOrders = ConcurrentHashMap.newKeySet();

    /**
     * 幂等的下单工具：同一 orderId 第二次调用直接返回上次的结果，不重复扣库存。
     * 对照记忆：submitOrder(orderId) 幂等；unsafeIncrement() 这类"每调一次就加一"的
     * 工具天然不幂等——如果业务必须依赖"恰好一次"，要么改成幂等写法，要么在调用方做去重。
     */
    @Tool(description = "按订单号提交采购订单。同一订单号重复提交是安全的，不会重复下单。")
    public String submitOrder(@ToolParam(description = "订单号，全局唯一") String orderId) {
        if (processedOrders.add(orderId)) { // add 返回 true = 第一次见到这个订单号
            return "订单 " + orderId + " 提交成功（首次处理）。";
        }
        return "订单 " + orderId + " 已存在，本次为重复提交，未重复处理（幂等保护生效）。";
    }

    // =========================================================================
    // 给 Controller 的只读视图：压测后读计数用（字段私有，只暴露读方法）
    // =========================================================================

    /** 反例计数器当前值（读它本身不需要同步，但读到的可能就是"错的"——这正是要展示的） */
    public long unsafeTotalView() {
        return unsafeTotal;
    }

    /** 正例计数器当前值（AtomicLong.get，读到的一定是某个真实写入过的值） */
    public long safeTotalView() {
        return safeTotal.get();
    }
}
