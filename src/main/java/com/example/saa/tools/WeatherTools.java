package com.example.saa.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * 【知识点 5】Tool Calling（工具调用）—— 第 1 部分：定义工具
 * ============================================================================
 *
 * Tool Calling 解决什么问题？
 *   大模型的训练数据有截止时间，也不知道你公司的私有数据、不知道现在几点。
 *   Tool Calling 让模型"借用"你写的 Java 方法去获取信息或执行动作：
 *   模型不直接执行方法，它只是决定"该调哪个方法、参数是什么"，真正执行发生在你的 JVM 里。
 *
 * 完整交互循环（理解这个循环比记住注解更重要）：
 *   1) 你的代码把"工具说明书"（方法签名+@Tool 描述）随请求发给模型；
 *   2) 模型判断当前问题需要工具 → 返回一个 tool_call 指令（方法名+参数），而不是回答文字；
 *   3) 框架（Spring AI 的 ToolCallingAdvisor）在本机执行对应方法，把结果作为消息回传；
 *   4) 模型拿到工具结果后，组织成最终自然语言回答。
 *   整个 2→3→4 的循环由框架自动完成，业务代码只写 .tools(toolObject) 一行。
 *
 * 注解说明：
 *   @Tool      —— 标在方法上，description 是写给"模型"看的说明书：什么时候该调这个方法。
 *                 描述写得越清楚，模型选工具、传参数越准。
 *   @ToolParam —— 标在参数上，同样用 description 告诉模型这个参数是什么、该填什么格式。
 *   （历史上这一机制叫 Function Calling / FunctionCallback，1.0 起统一为 @Tool 注解风格）
 *
 * 本类同时被两个地方使用：
 *   - ToolCallingController：注册给 ChatClient，让通义模型在对话中调用（本地演示）；
 *   - McpServerConfig：通过 MCP 协议暴露给外部 MCP 客户端（如 Claude Desktop）——
 *     同一份工具实现，两种"销售渠道"，这正是抽象的价值。
 */
@Component
public class WeatherTools {

    /**
     * 模拟的天气查询工具。真实场景里这里是 RPC / 数据库 / 三方 API 调用。
     * 注意方法必须是 public、可被反射调用；返回值可以是任意对象（会转成文本给模型）。
     */
    @Tool(description = "查询指定城市的当前天气，返回温度、天气状况与穿衣建议。当用户询问天气、温度、穿衣时调用此工具。")
    public String getWeather(@ToolParam(description = "城市名称，例如：广州、上海、杭州") String city) {
        // 模拟数据：教学 demo 不接真实气象 API，重点是让读者看懂"模型→工具→模型"的循环
        return city + "：晴，28℃，湿度 65%，微风；紫外线较强，建议穿轻薄透气的衣物，注意防晒。";
    }
}
