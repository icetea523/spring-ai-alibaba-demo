package com.example.saa.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ============================================================================
 * 【知识点 1】ChatModel 与 ChatClient：两种调用大模型的方式
 * ============================================================================
 *
 * ChatModel（底层）：
 *   - 与具体模型服务一一对应的"驱动"接口，负责发请求、收响应。
 *   - API 很"裸"：call(Prompt) 返回 ChatResponse，所有细节（消息包装、解析）都要自己处理。
 *   - 类比：JDBC 的 Connection——能用，但写业务太啰嗦。
 *
 * ChatClient（推荐的高层 API）：
 *   - 基于 ChatModel 的流式(fluent)封装，屏蔽了 Prompt 组装、消息拼接、Advisor 链等细节。
 *   - 类比：JdbcTemplate 之于 JDBC——业务代码都该用这层写。
 *   - 我们后面的 Memory / Advisor / RAG / Tool Calling 全部挂在 ChatClient 上使用。
 *
 * 【知识点 2】DashScope 接入在哪里发生？
 *   本工程引入了 spring-ai-alibaba-starter-dashscope，它在启动时自动装配了两个 Bean：
 *     1) ChatModel —— 默认实现是 DashScopeChatModel（通义千问系列）
 *     2) ChatClient.Builder —— 原型(prototype)作用域，注入后 build() 得到 ChatClient
 *   模型和 key 的配置见 src/main/resources/application.yml 里的 spring.ai.dashscope 段。
 *   把 starter 换成 spring-ai-starter-model-openai，下面这行代码不用改——这就是抽象层的价值。
 *
 * 测试：
 *   curl "http://localhost:8080/ai/chat-model?message=一句话介绍Spring AI"
 *   curl "http://localhost:8080/ai/chat-client?topic=Spring AI Alibaba&name=振海"
 */
@RestController
@RequestMapping("/ai")
public class BasicChatController {

    private final ChatModel chatModel;
    private final ChatClient chatClient;

    /**
     * 两个 Bean 都由 DashScope starter 自动装配，直接注入即可。
     *
     * ChatClient.Builder 是 prototype 作用域：每次注入都是新 Builder，
     * 因此可以在不同类里构建出"人设/默认行为"不同的多个 ChatClient。
     * 这里给本类设置了一个默认 System Prompt：对所有请求生效的"人设"。
     */
    public BasicChatController(ChatModel chatModel, ChatClient.Builder chatClientBuilder) {
        this.chatModel = chatModel;
        this.chatClient = chatClientBuilder
                .defaultSystem("你是一名简洁友好的中文技术助教，回答控制在 5 句话以内，多用例子。")
                .build();
    }

    /**
     * 方式一：直接使用最底层的 ChatModel。
     *   chatModel.call(new Prompt(...)) —— 发送 prompt，拿到完整 ChatResponse。
     *   ChatResponse 是个"信封"：包含生成的消息、token 用量、finishReason 等元数据，
     *   所以取正文要多走两步：getResult().getOutput().getText()。
     * 什么时候用：需要拿到 token 用量、多候选结果(generations)等原始信息时。
     */
    @GetMapping("/chat-model")
    public String chatModel(@RequestParam(defaultValue = "用一句话介绍 Spring AI Alibaba") String message) {
        return chatModel.call(new Prompt(message)).getResult().getOutput().getText();
    }

    /**
     * 方式二（推荐）：ChatClient 流式 API。
     *   .prompt()      开始构建一次请求
     *   .user(...)     用户消息；支持 {占位符} 模板，用 .param() 填充（底层是 PromptTemplate）
     *   .call()        同步调用，等完整响应
     *   .content()     只取正文 String（也可以 .chatResponse() 拿完整信封）
     * 什么时候用：绝大多数业务对话场景。Agent 框架底层同样基于 ChatClient。
     */
    @GetMapping("/chat-client")
    public String chatClient(
            @RequestParam(defaultValue = "Spring AI Alibaba") String topic,
            @RequestParam(defaultValue = "同学") String name) {
        return chatClient.prompt()
                // 模板占位符写法：{name} {topic} 会在发送前被替换成 param() 传入的值
                .user(u -> u.text("请给 {name} 用 3 句话介绍 {topic} 的核心思想，并给出一个生活化的类比。")
                        .param("name", name)
                        .param("topic", topic))
                .call()
                .content();
    }
}
