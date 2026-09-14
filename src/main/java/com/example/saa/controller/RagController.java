package com.example.saa.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ============================================================================
 * 【知识点 7】RAG —— 查询部分：QuestionAnswerAdvisor 一行接入
 * ============================================================================
 *
 * 查询侧只需要三步：
 *   1) 用户问题被 EmbeddingModel 转成向量；
 *   2) VectorStore 检索出最相近的 top-K 个文本块（默认 4 个）；
 *   3) 检索到的内容作为"参考资料"拼进 prompt，模型基于资料作答。
 *   这三步全部由 QuestionAnswerAdvisor 这一个 Advisor 完成——
 *   又一次印证：Advisor 是 Spring AI 横切能力（记忆/RAG/日志）的标准挂载点。
 *
 * 和 Memory 组合的顺序有讲究：
 *   MessageChatMemoryAdvisor 要放在 QuestionAnswerAdvisor 之前——
 *   先回放历史再检索，检索时能利用对话上下文里的信息（官方文档建议的顺序）。
 *
 * 进阶：QuestionAnswerAdvisor.builder(vectorStore)
 *          .searchRequest(SearchRequest.builder().topK(6).similarityThreshold(0.5).build())
 *          .build()   ← 可调检索条数与相似度阈值；本 demo 用默认值保持简单。
 *
 * 测试（问的问题答案就在 docs/ 知识文档里，对比"有没有 RAG"的回答质量）：
 *   curl "http://localhost:8080/ai/rag?question=Advisor在Spring AI里是什么角色？"
 *   curl "http://localhost:8080/ai/rag?question=这个demo有哪些接口可以玩？"
 */
@RestController
@RequestMapping("/ai")
public class RagController {

    private final ChatClient chatClient;
    private final SimpleVectorStore vectorStore;

    public RagController(ChatClient.Builder chatClientBuilder, SimpleVectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    @GetMapping("/rag")
    public String rag(@RequestParam(defaultValue = "这个demo覆盖了Spring AI的哪些知识点？") String question) {
        return chatClient.prompt()
                .user(question)
                // 核心一行：挂上 RAG Advisor，检索增强自动完成
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .call()
                .content();
    }
}
