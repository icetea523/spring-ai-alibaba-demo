package com.example.saa.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ============================================================================
 * 【知识点 6】Memory —— 第 2 部分：用 Advisor 把记忆接进调用链
 * 【知识点 6.5】Advisor：ChatClient 的"拦截器/管道"
 * ============================================================================
 *
 * 先讲 Advisor（理解它后面 RAG 也是同套路）：
 *   Advisor 是 ChatClient 调用链上的拦截器，按 order 顺序串成管道：
 *   请求发出前逐个"加工"prompt，响应回来后逐个"加工"结果——
 *   思想完全等同于 Servlet Filter / Spring AOP。
 *   官方内置：MessageChatMemoryAdvisor（挂记忆）、QuestionAnswerAdvisor（挂 RAG 检索）、
 *   SimpleLoggerAdvisor（打印请求/响应日志）等；也可以自己实现 CallAdvisor 扩展。
 *
 * MessageChatMemoryAdvisor 做的事：
 *   每次调用前，从 ChatMemory 里按 conversationId 取出历史消息，拼进本次 prompt；
 *   响应回来后，把本轮新的 user/assistant 消息写回 ChatMemory。
 *
 * conversationId 必须每次显式传入（.param）：
 *   ChatMemory.CONVERSATION_ID 是约定的参数 key，漏传会直接抛 IllegalArgumentException——
 *   这是 1.1.x 的明确行为，比老版本的静默默认值更安全。
 *
 * 测试（同一个 conversationId 连续两次调用，第二次能"记得"第一次的信息）：
 *   curl "http://localhost:8080/ai/memory/chat?conversationId=u001&message=我叫振海，9年Java开发"
 *   curl "http://localhost:8080/ai/memory/chat?conversationId=u001&message=我叫什么？做什么工作的？"
 *   换成 conversationId=u002 再问同样的问题——它会答不上来，这就是会话隔离。
 */
@RestController
@RequestMapping("/ai/memory")
public class MemoryAdvisorController {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public MemoryAdvisorController(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder.build();
        this.chatMemory = chatMemory;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "u001") String conversationId,
                       @RequestParam(defaultValue = "你好，请记住我们正在学习 Spring AI Alibaba") String message) {
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a
                        // 挂载记忆 Advisor：调用前回放历史、调用后写回历史
                        .advisors(
                                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                                // 日志 Advisor：把最终发出的 prompt 与模型响应打印到控制台（DEBUG 级别）
                                // 建议放链尾，且生产环境慎开（可能打出敏感内容）
                                new SimpleLoggerAdvisor())
                        // 告诉记忆 Advisor 这次属于哪个会话
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
