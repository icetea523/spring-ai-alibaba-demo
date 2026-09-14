package com.example.saa.graph;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * ============================================================================
 * 【知识点 9】Graph 工作流编排（Spring AI Alibaba Graph）
 * ============================================================================
 *
 * ChatClient 模式 vs Graph 模式：
 *   ChatClient：一次调用 = 一次模型交互，控制流写死在 Java 代码里（顺序/分支靠 if）。
 *   Graph：把整个任务建模成一张"状态图"，节点(node)是工作单元（可以是 LLM 调用、
 *   普通函数、甚至另一个子图），边(edge)定义流转，条件边(conditional edge)实现路由。
 *   适合：多步骤固定流程（意图识别→路由→处理）、多智能体协作、需要中间状态检查点的场景。
 *   心智模型：非常像 LangGraph / 工作流引擎，SAA Graph 是 Java 生态的对应实现。
 *
 * 核心概念（对照本例）：
 *   OverAllState     全局共享状态：一个 Map，所有节点读写它
 *   KeyStrategy      同一个 key 被多个节点写入时的合并策略：
 *                      ReplaceStrategy 覆盖 / AppendStrategy 追加（常用于消息列表）
 *   NodeAction       节点接口：入参 OverAllState，返回 Map（即本节点要写回状态的增量）
 *   EdgeAction       条件边的路由函数：读状态 → 返回下一个节点名
 *   START / END      特殊起止节点
 *   node_async / edge_async  把同步动作包装成异步执行（框架统一调度）
 *   compile()        编译图：做结构校验（如是否有孤立节点），返回 CompiledGraph
 *
 * 本例流程（一个最小但完整的"路由工作流"）：
 *                      ┌─ concept → explain（概念讲解节点）─┐
 *   START → classifier │                                  │ → END
 *                      └─ code    → coding  （代码实战节点）─┘
 *   classifier 是 LLM 节点：判断问题是"概念类"还是"代码类"；
 *   条件边根据分类结果路由到不同的处理节点——这就是意图识别 + 分流的经典工作流模式。
 *
 * 进阶方向（README 有指引）：
 *   多智能体 = 多个 Agent 节点 + Supervisor/路由条件边的组合；
 *   持久化 checkpoint、human-in-the-loop 中断恢复、子图复用等能力见官方 Graph 教程。
 */
@Configuration
public class GraphConfig {

    /**
     * 构建"智能学习路由"工作流并编译成 CompiledGraph。
     * 注入的 ChatClient.Builder 由 DashScope 自动配置提供——Graph 节点内部照样用 ChatClient 调模型。
     */
    @Bean
    public StateGraph studyGraph(ChatClient.Builder chatClientBuilder) throws Exception {
        ChatClient chatClient = chatClientBuilder.build();

        // 状态定义：声明图里流转哪些 key，以及各自写入策略（本例全部"后写覆盖先写"）
        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("query", new ReplaceStrategy());     // 用户原始问题
            strategies.put("category", new ReplaceStrategy());  // 分类结果：concept / code
            strategies.put("answer", new ReplaceStrategy());    // 最终回答
            return strategies;
        };

        StateGraph graph = new StateGraph(keyStrategyFactory)

                // ── 节点 1：classifier（LLM 分类节点）──────────────────────────────
                // 读取 state 里的 query，让模型分类，把结果写回 state 的 "category" key。
                // node_async(...) 把 NodeAction 包装成异步节点；lambda 就是 NodeAction.apply。
                .addNode("classifier", node_async(state -> {
                    String query = (String) state.value("query").orElse("");
                    String category = chatClient.prompt()
                            .system("你是问题分类器。判断用户问题属于概念理解类还是代码实践类，"
                                    + "只返回 concept 或 code 两个词之一，不要返回任何其他内容。")
                            .user(query)
                            .call()
                            .content();
                    // 容错：模型输出不规范时兜底为 concept（真实项目里这里应该更严格）
                    String normalized = category != null && category.toLowerCase().contains("code")
                            ? "code" : "concept";
                    // 返回的 Map 就是"本节点要写回全局状态的增量"
                    return Map.of("category", normalized);
                }))

                // ── 节点 2：explain（概念讲解分支）────────────────────────────────
                .addNode("explain", node_async(state -> {
                    String query = (String) state.value("query").orElse("");
                    String answer = chatClient.prompt()
                            .system("你是技术讲解老师，面向有 9 年经验的 Java 工程师，"
                                    + "用通俗语言加一个生活化类比解释概念，不超过 5 句话。")
                            .user(query)
                            .call()
                            .content();
                    return Map.of("answer", answer);
                }))

                // ── 节点 3：coding（代码实战分支）────────────────────────────────
                .addNode("coding", node_async(state -> {
                    String query = (String) state.value("query").orElse("");
                    String answer = chatClient.prompt()
                            .system("你是资深 Java 工程师，回答必须包含可直接运行的代码示例并逐段解释。")
                            .user(query)
                            .call()
                            .content();
                    return Map.of("answer", answer);
                }))

                // ── 边：定义流转 ────────────────────────────────────────────────
                .addEdge(START, "classifier")
                // 条件边：edge_async 里的 EdgeAction 读状态返回"下一个节点名"，
                // 第二个参数 Map 是 返回值 → 节点 的路由表。
                .addConditionalEdges("classifier",
                        edge_async(state -> (String) state.value("category").orElse("concept")),
                        Map.of("concept", "explain", "code", "coding"))
                // 两个分支节点执行完都直接结束
                .addEdge("explain", END)
                .addEdge("coding", END);

        return graph;
        // 编译动作放在 GraphController 的构造器里做（stateGraph.compile()），
        // 便于读者看到"定义"与"编译"是分离的两个步骤。
    }
}
