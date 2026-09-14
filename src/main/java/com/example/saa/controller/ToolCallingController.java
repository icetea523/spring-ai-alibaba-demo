package com.example.saa.controller;

import com.example.saa.tools.DateTimeTools;
import com.example.saa.tools.WeatherTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ============================================================================
 * 【知识点 5】Tool Calling —— 第 2 部分：注册工具并观察调用
 * ============================================================================
 *
 * 注册方式（本 demo 使用对象注册）：
 *   .tools(weatherTools, dateTimeTools)
 *   框架扫描对象里所有 @Tool 方法生成"工具说明书"。
 *   另有静态注册：ChatClient.builder().defaultTools(...)（对整个 client 生效）。
 *
 * 学习建议：把 application.yml 里 advisor 日志级别开到 DEBUG（已配置），
 * 调用本接口时观察控制台，能看到模型发出的 tool_call 与工具执行结果回传，
 * 这是理解"模型→工具→模型"循环最直观的方式。
 *
 * 测试（问题里故意包含"天气"和"时间"两类诉求，观察模型分别调用不同工具）：
 *   curl "http://localhost:8080/ai/tool?question=广州今天天气怎么样？我现在该穿什么？"
 *   curl "http://localhost:8080/ai/tool?question=现在几点了？我还能赶上下周前的最后一个工作日吗？"
 *   curl "http://localhost:8080/ai/tool?question=帮我推荐一部电影"   ← 不需要工具，模型直接回答
 */
@RestController
@RequestMapping("/ai")
public class ToolCallingController {

    private final ChatClient chatClient;

    private final WeatherTools weatherTools;
    private final DateTimeTools dateTimeTools;

    public ToolCallingController(ChatClient.Builder chatClientBuilder,
                                 WeatherTools weatherTools,
                                 DateTimeTools dateTimeTools) {
        this.chatClient = chatClientBuilder.build();
        this.weatherTools = weatherTools;
        this.dateTimeTools = dateTimeTools;
    }

    @GetMapping("/tool")
    public String tool(@RequestParam(defaultValue = "广州今天天气怎么样？我现在该穿什么？") String question) {
        return chatClient.prompt()
                .user(question)
                // 核心一行：把工具对象注册进本次调用。
                // ChatClient 会自动注册 ToolCallingAdvisor 负责"执行模型点名的工具"这一环节。
                .tools(weatherTools, dateTimeTools)
                .call()
                .content();
    }
}
