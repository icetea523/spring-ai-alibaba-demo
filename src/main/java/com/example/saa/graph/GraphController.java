package com.example.saa.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 【知识点 9 · 续】运行 Graph 工作流
 *
 * StateGraph 是"图纸"（可修改），CompiledGraph 是"成品"（校验过、不可改、直接执行）：
 *   - 构造器里 compile()：启动时编译一次，之后复用（官方推荐的预编译做法）；
 *   - invoke()：同步执行整张图，返回最终 OverAllState；
 *   - RunnableConfig.threadId：本次执行的"线程 ID"，接入 checkpoint 持久化后，
 *     同一 threadId 的多次执行可以共享/恢复状态（记忆的基础，进阶主题）。
 *
 * 测试：
 *   curl "http://localhost:8080/agent/graph/run?query=什么是Advisor？"
 *        → 被路由到 explain 节点（概念讲解风格回答）
 *   curl "http://localhost:8080/agent/graph/run?query=怎么用Spring AI写一个流式接口？"
 *        → 被路由到 coding 节点（带代码示例的回答）
 *   返回的 JSON 就是整张图的最终状态：query / category / answer 三个 key。
 */
@RestController
@RequestMapping("/agent/graph")
public class GraphController {

    private final CompiledGraph compiledGraph;

    public GraphController(StateGraph studyGraph) throws Exception {
        // 编译：做图结构校验，得到可执行的 CompiledGraph
        this.compiledGraph = studyGraph.compile();
    }

    @GetMapping("/run")
    public Map<String, Object> run(@RequestParam(defaultValue = "什么是 Spring AI 的 Advisor 机制？") String query) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId("graph-demo-" + System.currentTimeMillis())
                .build();

        // 执行整张图：传入初始状态（用户问题），拿到最终状态
        Optional<OverAllState> result = compiledGraph.invoke(Map.of("query", query), config);

        // OverAllState.data() 返回全部状态的 Map 视图，直接作为接口响应
        return result.map(OverAllState::data).orElseGet(HashMap::new);
    }
}
