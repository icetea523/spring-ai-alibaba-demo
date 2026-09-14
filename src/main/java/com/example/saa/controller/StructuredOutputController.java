package com.example.saa.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ============================================================================
 * 【知识点 4】结构化输出（Structured Output）
 * ============================================================================
 *
 * 要解决的问题：
 *   大模型默认返回"一段话"（自然语言），但业务代码想要的是"对象"——
 *   比如直接拿到一个 MovieRecommendation 实例，字段齐、类型对，可以直接入库/返给前端。
 *
 * Spring AI 的做法（两步，全自动）：
 *   1) BeanOutputConverter 根据你的 Java 类型自动生成一段 JSON Schema 描述，
 *      作为额外指令拼进 prompt，告诉模型"请严格按这个 JSON 格式返回"。
 *   2) 模型返回 JSON 文本后，同一个转换器自动反序列化成你的 Java 对象。
 *
 *   在 ChatClient 上这一切被压缩成一行：
 *       .call().entity(MovieRecommendation.class)
 *
 * 前提注意：
 *   模型要"听话"地返回 JSON——通义 qwen 系列对 JSON 指令遵从良好；
 *   记录(record)的所有字段名会成为 schema 的一部分，字段名要起得语义清晰，
 *   模型是按字段名的"含义"去填内容的。
 *
 * 测试：
 *   curl "http://localhost:8080/ai/struct?preference=想找一部关于人工智能的科幻片"
 *   观察返回的 JSON：已经是一个结构化对象，不再是自由文本。
 */
@RestController
@RequestMapping("/ai")
public class StructuredOutputController {

    private final ChatClient chatClient;

    public StructuredOutputController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 用 record 定义期望的输出结构（Java 17 语法，非常适合做不可变 DTO）。
     * Spring AI 会根据字段名+类型生成 JSON Schema：
     *   title 片名、genre 类型、rating 评分(1-10)、reasons 推荐理由列表、
     *   protagonist 主角名。
     */
    public record MovieRecommendation(
            String title,
            String genre,
            Double rating,
            List<String> reasons,
            String protagonist) {
    }

    /**
     * .entity(Class) 是结构化输出的核心：返回值直接就是强类型对象。
     * Spring MVC 再把它序列化成 JSON 返回给调用方。
     */
    @GetMapping("/struct")
    public MovieRecommendation struct(
            @RequestParam(defaultValue = "想找一部关于人工智能的科幻片") String preference) {
        return chatClient.prompt()
                .user("根据我的偏好推荐一部电影：" + preference)
                .call()
                .entity(MovieRecommendation.class);
    }
}
