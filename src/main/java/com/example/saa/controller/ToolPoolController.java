package com.example.saa.controller;

import com.example.saa.config.ToolPoolConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * ============================================================================
 * 【知识点 10】动态工具池（Tool Pool）—— 按需挂载与运行时摘挂
 * ============================================================================
 *
 * 与知识点 5 的核心区别（对比着看才明白"动态"在哪）：
 *   知识点 5：.tools(weatherTools, dateTimeTools) —— 编译期写死，每个请求工具面相同；
 *   知识点 10：.toolCallbacks(pool.mountFor(groups)) —— 运行期决定本请求挂载哪个子集，
 *             同一个 ChatClient，不同请求可以拿到完全不同的工具面。
 *
 * 知识点：
 *   - ChatClient 的 .tools(Object...) 是"异构"入口（对象/回调/Provider 都能传）；
 *     当手里已经是一份 ToolCallback 列表时，用 .toolCallbacks(List<ToolCallback>) 更直接，
 *     免去再次反射扫描。
 *   - 挂载子集 = 池子按 groups 过滤 + enabled 开关二次过滤（两层过滤逻辑见 ToolPoolConfig.mountFor）。
 *
 * 测试清单（按顺序执行，观察工具面对模型能力的影响）：
 *
 *   # 1. 看池子全貌：有哪些组、每组有哪些工具、当前启用了哪些组
 *   curl "http://localhost:8080/ai/tool-pool/status"
 *
 *   # 2. 只挂 travel 组：问机票 → 正常调用工具
 *   curl "http://localhost:8080/ai/tool-pool?groups=travel&question=广州去上海有什么航班？顺便推荐个酒店"
 *
 *   # 3. 只挂 travel 组：问汇率 → 模型看不到汇率工具，只能坦白没有该能力（重点观察！）
 *   curl "http://localhost:8080/ai/tool-pool?groups=travel&question=美元兑人民币汇率是多少？"
 *
 *   # 4. 挂 travel+finance：同样的汇率问题，现在能查了（对照组，体会"挂载了什么才会什么"）
 *   curl "http://localhost:8080/ai/tool-pool?groups=travel,finance&question=美元兑人民币汇率是多少？"
 *
 *   # 5. 运行时摘挂 finance 组（不重启应用）
 *   curl "http://localhost:8080/ai/tool-pool/toggle?group=finance&enabled=false"
 *   curl "http://localhost:8080/ai/tool-pool?groups=finance&question=100人民币换多少日元？"   ← 工具已被摘掉
 *   curl "http://localhost:8080/ai/tool-pool/toggle?group=finance&enabled=true"
 *   curl "http://localhost:8080/ai/tool-pool?groups=finance&question=100人民币换多少日元？"   ← 又能查了
 */
@RestController
@RequestMapping("/ai/tool-pool")
public class ToolPoolController {

    private final ChatClient chatClient;
    private final ToolPoolConfig toolPool;

    public ToolPoolController(ChatClient.Builder chatClientBuilder, ToolPoolConfig toolPool) {
        // ChatClient 只构建一次；工具面是"每次请求"决定的事，不需要为不同子集建多个 client
        this.chatClient = chatClientBuilder.build();
        this.toolPool = toolPool;
    }

    /**
     * 按需挂载：本次请求要哪些组，就从池子里挑哪些组的工具。
     * groups 参数用英文逗号分隔多个组名，例如 groups=travel,finance。
     */
    @GetMapping
    public String toolPool(@RequestParam(defaultValue = "travel") String groups,
                           @RequestParam(defaultValue = "广州去上海有什么航班？顺便推荐个酒店") String question) {
        List<String> requestedGroups = Arrays.asList(groups.split(","));
        List<ToolCallback> mounted = toolPool.mountFor(requestedGroups);

        if (mounted.isEmpty()) {
            // 提前拦截：一个工具都不挂时没有调模型的必要（省一次 token），直接说明原因
            return "本次未挂载任何工具（请求的组都不存在或都未启用）。当前启用的组：" + toolPool.enabledGroups();
        }

        return chatClient.prompt()
                .user(question)
                // 核心一行：本请求的工具面 = 池子里过滤出来的子集。
                // .toolCallbacks(List) 直接接收 ToolCallback 列表，是最精确的挂载方式。
                .toolCallbacks(mounted)
                .call()
                .content();
    }

    /** 池子状态：组 → 工具名清单 + 当前启用的组。调优工具面之前先看这里。 */
    @GetMapping("/status")
    public String status() {
        return "工具池分组：" + toolPool.poolOverview()
                + "；当前启用：" + toolPool.enabledGroups();
    }

    /**
     * 运行时摘挂开关：enabled=false 摘除某组，true 挂回。
     * 典型用途：下游服务故障时先摘掉它的工具止血（模型不会再调用一个必失败的工具），恢复后再挂回。
     */
    @GetMapping("/toggle")
    public String toggle(@RequestParam String group, @RequestParam boolean enabled) {
        boolean ok = toolPool.setGroupEnabled(group, enabled);
        return ok
                ? "已" + (enabled ? "启用" : "停用") + "分组 [" + group + "]。当前启用：" + toolPool.enabledGroups()
                : "分组 [" + group + "] 不存在。可用分组：" + toolPool.poolOverview().keySet();
    }
}
