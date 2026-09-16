package com.example.saa.controller;

import com.example.saa.config.SessionToolRegistry;
import com.example.saa.tools.ConcurrencyDemoTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ============================================================================
 * 【知识点 11】并发工具调用 —— 观察时序 / 竞态压测 / 会话隔离
 * ============================================================================
 *
 * 三个接口各演示一件事，建议按顺序玩：
 *   1) /ai/tool-parallel   模型一次返回多个 tool_call 时的执行时序（事实观察）
 *   2) /ai/tool-race       同一个工具被线程池真并发调用的竞态对照（反例 vs 正例）
 *   3) /ai/tool-session    有状态工具"每会话一实例"的隔离效果
 *
 * 三个接口共同回答一个工程问题：工具代码要按什么标准写？
 *   答案：按"随时可能被多线程同时调进"来写——要么无状态，要么并发容器 + 原子类，
 *   要么每会话一实例；写操作类工具还要幂等（重复调用不产生重复副作用）。
 *
 * 并发三件套（本文件都用到了，找对应代码行）：
 *   - 并发上限：/ai/tool-race 用固定大小线程池限制同时执行的任务数；
 *     如果限的是"共享外部资源"（数据库连接、三方 API QPS），用 Semaphore 更合适：
 *       Semaphore permits = new Semaphore(8);
 *       permits.acquire();  try { 调外部资源; } finally { permits.release(); }
 *   - 超时：CompletableFuture.orTimeout(...) 给每个任务兜底，避免一个慢调用拖死整批；
 *   - 幂等：见 ConcurrencyDemoTools.submitOrder —— 查询类天然幂等，写操作类必须自己保证。
 */
@RestController
@RequestMapping("/ai")
public class ConcurrencyToolController {

    private final ChatClient chatClient;
    private final ConcurrencyDemoTools concurrencyTools;
    private final SessionToolRegistry sessionRegistry;

    public ConcurrencyToolController(ChatClient.Builder chatClientBuilder,
                                     ConcurrencyDemoTools concurrencyTools,
                                     SessionToolRegistry sessionRegistry) {
        this.chatClient = chatClientBuilder.build();
        this.concurrencyTools = concurrencyTools;
        this.sessionRegistry = sessionRegistry;
    }

    // =========================================================================
    // 1) 观察时序：模型一次返回多个 tool_call 怎么执行？
    // =========================================================================

    /**
     * 问题里故意安排三个互不依赖的报告诉求，诱导模型在一次响应里返回多个 tool_call。
     *
     * 怎么观察：返回值末尾附了工具执行时间线（起止时间戳 + 线程名 + 耗时）。
     * Spring AI 1.1.2 的 DefaultToolCallingManager 是【串行逐个执行】的，
     * 所以你会看到三个调用时间戳首尾相接、线程名相同，总耗时 ≈ 3 × 1.5 秒。
     *
     * 这说明什么？
     *   - 框架层的"多 tool_call 并行"在这个版本里没有开（builder 也无 parallel 选项）；
     *   - 但【不要】因此认为工具不需要线程安全——多用户同时请求时，
     *     各自的工具执行循环落在同一个单例工具上，并发是常态（见 /ai/tool-race）；
     *   - 真的要给"一批独立调用"提速时，在业务层自己编排（见 /ai/tool-race 的
     *     ExecutorService 写法），或把慢工具内部异步化后立即返回受理结果。
     *
     * 测试：
     *   curl "http://localhost:8080/ai/tool-parallel"
     */
    @GetMapping("/tool-parallel")
    public String toolParallel(
            @RequestParam(defaultValue = "请分别生成咖啡、茶叶、可可三个主题的市场简报，尽量一次性都查出来。") String question) {
        concurrencyTools.drainTimeline(); // 清空上一轮实验的时间线，避免混淆

        String answer = chatClient.prompt()
                .user(question)
                .tools(concurrencyTools) // 一个 @Component 对象里的多个 @Tool 方法都会被挂载
                .call()
                .content();

        return answer + "\n\n—— 工具执行时间线（观察时间戳是否重叠、线程名是否相同）——\n"
                + String.join("\n", concurrencyTools.drainTimeline());
    }

    // =========================================================================
    // 2) 竞态压测：反例 vs 正例，眼见为实
    // =========================================================================

    /**
     * 不经过模型，直接用线程池并发调用同一个工具方法 threads × loops 次。
     * 为什么绕过模型？竞态是确定性的并发 bug，压测要跑得快、可重复；
     * 走模型又慢又受"模型发不发调用"影响，不适合做对照实验。
     *
     * 预期结果：unsafeTotal < threads×loops（丢更新），safeTotal == threads×loops（精确）。
     *
     * 测试（默认 8 线程 × 10000 次；第一次跑完 unsafe 计数几乎必然对不上账）：
     *   curl "http://localhost:8080/ai/tool-race"
     *   curl "http://localhost:8080/ai/tool-race?threads=16&loops=20000"
     */
    @GetMapping("/tool-race")
    public String toolRace(@RequestParam(defaultValue = "8") int threads,
                           @RequestParam(defaultValue = "10000") int loops) {
        // 【并发上限】池大小 = 同时执行的工具调用的上限。超出部分在队列里排队——
        // 这就是最简单的限流；线程池参数（队列长度、拒绝策略）生产上要显式设置。
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        try {
            for (int t = 0; t < threads; t++) {
                final int threadNo = t;
                CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < loops; i++) {
                        try {
                            concurrencyTools.unsafeIncrement("k-" + threadNo); // 反例
                            concurrencyTools.safeIncrement("k-" + threadNo);   // 正例
                        } catch (Exception e) {
                            // 反例的 HashMap 在极端并发下还可能直接抛异常——也记下来一起展示
                            errors.add("线程" + threadNo + ": " + e.getClass().getSimpleName());
                        }
                    }
                }, pool)
                // 【超时兜底】给每个任务设死线：慢调用/死循环不允许拖死整批。
                // 本实验任务很快触发不了超时，但这是生产代码的标准姿势。
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(e -> {
                    errors.add("任务失败: " + e.getClass().getSimpleName());
                    return null;
                });
                futures.add(f);
            }
            // 等全部任务结束（超时保护之外的整体等待上限）
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            errors.add("编排失败: " + e.getClass().getSimpleName());
        } finally {
            pool.shutdown(); // 用完必须关，否则线程泄漏
        }

        long expected = (long) threads * loops;
        return "并发工具调用压测：线程 " + threads + " × 每线程 " + loops + " 次，预期总数 " + expected + "\n"
                + "反例（HashMap + long++）实际计数：" + concurrencyTools.unsafeTotalView() + "（< 预期 = 丢更新实锤）\n"
                + "正例（ConcurrentHashMap + AtomicLong）实际计数：" + concurrencyTools.safeTotalView() + "（== 预期 = 正确）\n"
                + "异常/超时记录：" + (errors.isEmpty() ? "无" : errors);
    }

    // =========================================================================
    // 3) 会话隔离：每会话一实例的有状态工具
    // =========================================================================

    /**
     * 同一段代码、不同 conversationId，看到的是完全隔离的记事本。
     * 挂载方式：sessionRegistry.forSession(id) 返回"这个会话专属的工具实例"，
     * 挂进本次 ChatClient 调用——对比 /ai/tool-parallel 里挂的是全局单例，
     * 这就是"无状态工具全局一份 / 有状态工具每会话一份"的分水岭。
     *
     * 测试（第三步换 id 是灵魂）：
     *   curl "http://localhost:8080/ai/tool-session?conversationId=u1&message=记一条笔记：明天上午开评审会"
     *   curl "http://localhost:8080/ai/tool-session?conversationId=u1&message=我记过哪些笔记？"
     *   curl "http://localhost:8080/ai/tool-session?conversationId=u2&message=我记过哪些笔记？"   ← 查不到 u1 的
     */
    @GetMapping("/tool-session")
    public String toolSession(@RequestParam(defaultValue = "u1") String conversationId,
                              @RequestParam(defaultValue = "记一条笔记：明天上午开评审会") String message) {
        String answer = chatClient.prompt()
                .user(message)
                // 核心一行：挂载的是"会话专属实例"，不是全局单例
                .tools(sessionRegistry.forSession(conversationId))
                .call()
                .content();
        return answer + "\n\n（当前系统内存活的会话工具实例数：" + sessionRegistry.sessionCount() + "）";
    }
}
