package com.example.saa.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * ============================================================================
 * 【知识点 3】流式输出（Streaming）
 * ============================================================================
 *
 * 为什么需要流式？
 *   大模型生成一整段回答可能要几秒到几十秒，同步等待体验很差。
 *   流式让模型每生成一小段 token 就立刻推给客户端，也就是你在 ChatGPT 里看到的
 *   "打字机效果"。
 *
 * Spring AI 怎么做流式？
 *   把 .call() 换成 .stream()，返回值从 String 变成 Reactor 的 Flux<String>：
 *     - call().content()   → String          （一次性拿到全文）
 *     - stream().content() → Flux<String>    （一串增量文本块，逐个到达）
 *
 * HTTP 层怎么表现？
 *   - 前端标准方案是 SSE（Server-Sent Events）。
 *   - Spring MVC 天然支持响应式返回值：只要类路径上有 Reactor（Spring AI 自带依赖），
 *     Controller 方法直接返回 Flux，并声明 produces = text/event-stream，
 *     Spring MVC 会自动把它转成 SSE 推流，不需要额外引入 WebFlux。
 *   - 用浏览器直接访问这个接口，就能看到文字一段一段"长"出来。
 *
 * 测试：
 *   curl -N "http://localhost:8080/ai/stream?message=用三段话解释什么是RAG"
 *   （-N 表示关闭 curl 缓冲，能看到逐块输出）
 */
@RestController
@RequestMapping("/ai")
public class StreamController {

    private final ChatClient chatClient;

    public StreamController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 对比记忆：
     *   同步：.prompt().user(...).call().content()
     *   流式：.prompt().user(...).stream().content()   —— 只有这两处不同！
     *
     * 返回 Flux<String>：每个元素是一小段增量文本（不是全文重复），
     * 前端按到达顺序拼接即可还原全文。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam(defaultValue = "用三段话解释什么是 RAG，每段不超过 50 字") String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }
}
