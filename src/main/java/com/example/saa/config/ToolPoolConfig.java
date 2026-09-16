package com.example.saa.config;

import com.example.saa.tools.pool.ExchangeRateTools;
import com.example.saa.tools.pool.FlightTools;
import com.example.saa.tools.pool.HotelTools;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

/**
 * ============================================================================
 * 【知识点 10】动态工具池（Tool Pool）—— 统一注册 / 分组 / 动态挂载
 * ============================================================================
 *
 * 是什么？
 *   把散落在各个类里的 @Tool 工具，在应用启动时统一"盘点入库"：
 *     - 注册：用 ToolCallbacks.from(工具对象) 把每个对象里的 @Tool 方法
 *       批量转成 ToolCallback（框架的"工具说明书"载体），转换一次、反复复用；
 *     - 分组：给每个工具打一个业务组标签（travel / finance / ...），
 *       维护成 组名 → 该组工具列表 的映射；
 *     - 挂载：每次请求只把"该用的子集"发给模型，而不是整池全量。
 *
 * 为什么这么做？
 *   1) 省钱：工具说明书随请求发给模型、按 token 计费，全量挂载 = 每个请求都为
 *      用不到的工具付钱；只挂需要的子集，直接降低每次请求成本。
 *   2) 提准：候选工具越多、越相似，模型选错的概率越高；缩小候选集是最便宜的
 *      提升工具调用准确率的手段（官方 Tool Search 工具解决的是同一问题的高级版）。
 *   3) 治理：统一入口让"上线/下线一个工具"变成改一行注册代码 + 一个开关，
 *      而不是满工程找 .tools(...) 调用点。
 *
 * 两种"动态"粒度（本类两个机制分别对应）：
 *   - 请求级动态：每次请求带 groups 参数，从池子里选子集挂载（ToolPoolController）——
 *     这是"同一个 ChatClient，不同请求不同工具面"，最常用；
 *   - 运行级动态：enabledGroups 开关集合可以在应用运行期间改变（无需重启），
 *     适合"某个下游服务故障了，临时摘掉它的工具；恢复后再挂回来"的运维场景。
 *
 * 怎么用？（见 ToolPoolController 的三个接口）
 *
 * 怎么测？
 *   curl "http://localhost:8080/ai/tool-pool/status"
 *   curl "http://localhost:8080/ai/tool-pool/toggle?group=finance&enabled=false"
 *   curl "http://localhost:8080/ai/tool-pool?groups=finance&question=美元汇率多少？"   ← 已摘除，模型只能答没有该能力
 *   curl "http://localhost:8080/ai/tool-pool/toggle?group=finance&enabled=true"
 *   curl "http://localhost:8080/ai/tool-pool?groups=finance&question=美元汇率多少？"   ← 挂回来了
 */
@Configuration
public class ToolPoolConfig {

    /**
     * 池子的核心结构：组名 → 该组的 ToolCallback 列表。
     * 为什么用 ConcurrentHashMap？enabledGroups 是运行期可变的，
     * 读（挂载）与写（开关）在不同线程发生，必须用并发容器而不是普通 HashMap。
     */
    private final Map<String, List<ToolCallback>> toolPool = new ConcurrentHashMap<>();

    /**
     * 运行期分组开关：开关里的组才会被挂载。
     * ConcurrentHashMap.newKeySet() = 并发安全的 Set。
     */
    private final Set<String> enabledGroups = ConcurrentHashMap.newKeySet();

    /**
     * 启动时盘点入库：每个工具对象只做一次 @Tool → ToolCallback 的反射转换。
     * 注意 ToolCallbacks.from() 是 Spring AI 的静态工具类（org.springframework.ai.support.ToolCallbacks），
     * 它扫描对象上所有 @Tool 方法并生成工具定义（名称、描述、参数 JSON Schema）。
     * 后续挂载都是复用这份转换结果，避免每个请求重复反射。
     */
    @PostConstruct
    public void initToolPool() {
        registerGroup("travel", new FlightTools(), new HotelTools());
        registerGroup("finance", new ExchangeRateTools());

        // 默认全部组可用；生产里也可以默认全关、由配置中心/管理接口逐组打开
        enabledGroups.addAll(toolPool.keySet());
    }

    private void registerGroup(String group, Object... toolObjects) {
        // ToolCallbacks.from(Object...) 接收若干对象，返回该批对象全部 @Tool 方法对应的 ToolCallback
        toolPool.put(group, List.of(ToolCallbacks.from(toolObjects)));
    }

    /**
     * 【核心方法】按本次请求的组名单挂载子集：
     * 请求的组 ∩ 当前启用的组 = 实际发给模型的工具。
     *
     * 挂载顺序 = 组名单顺序 + 组内注册顺序。保持稳定顺序有两个好处：
     * 便于人工核对工具清单；也避免每次请求工具排列抖动。
     *
     * @param requestedGroups 请求想用的分组，如 ["travel", "finance"]；空列表 = 不挂任何工具
     */
    public List<ToolCallback> mountFor(List<String> requestedGroups) {
        List<ToolCallback> mounted = new ArrayList<>();
        for (String group : requestedGroups) {
            if (enabledGroups.contains(group)) {
                mounted.addAll(toolPool.getOrDefault(group, List.of()));
            }
        }
        return mounted;
    }

    /**
     * 运行期开关：false = 从"可挂载集合"里摘除该组（工具实例还在池子里，随时可挂回）。
     * 这就是"动态摘挂"——不改代码、不重启，下游故障时先把相关工具摘掉止血。
     */
    public boolean setGroupEnabled(String group, boolean enabled) {
        if (!toolPool.containsKey(group)) {
            return false; // 组不存在，让 Controller 返回明确错误而不是静默成功
        }
        if (enabled) {
            enabledGroups.add(group);
        } else {
            enabledGroups.remove(group);
        }
        return true;
    }

    /** 池子全貌：组名 → 该组工具的方法名清单（给 /status 接口用） */
    public Map<String, List<String>> poolOverview() {
        Map<String, List<String>> overview = new LinkedHashMap<>();
        toolPool.forEach((group, callbacks) -> {
            List<String> names = new ArrayList<>();
            for (ToolCallback cb : callbacks) {
                names.add(cb.getToolDefinition().name());
            }
            overview.put(group, names);
        });
        return overview;
    }

    /** 当前启用的组（只读视图，防止外部直接改内部状态） */
    public Collection<String> enabledGroups() {
        return Collections.unmodifiableCollection(enabledGroups);
    }
}
