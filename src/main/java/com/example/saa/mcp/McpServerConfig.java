package com.example.saa.mcp;

import com.example.saa.tools.DateTimeTools;
import com.example.saa.tools.WeatherTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ============================================================================
 * 【知识点 8】MCP（Model Context Protocol，模型上下文协议）
 * ============================================================================
 *
 * MCP 是什么？
 *   Anthropic 发起的开放协议，标准化"AI 应用 ↔ 外部工具/数据源"的连接方式。
 *   类比：USB-C 之于外设——过去每个 AI 应用要单独集成每个工具（N×M 问题），
 *   有了 MCP，工具方实现一次 MCP Server（M），任意 MCP 客户端（N）都能即插即用。
 *
 * 角色：
 *   MCP Server：能力提供方，暴露 tools / resources / prompts。
 *   MCP Client：宿主在 AI 应用（Claude Desktop、Cursor、你的 Spring Boot 应用）里，
 *               发现并调用 Server 的能力。
 *
 * 本工程的 MCP Server（spring-ai-starter-mcp-server-webmvc）：
 *   - 传输方式：HTTP + SSE（WebMVC 版），启动后自动暴露 MCP 端点，具体路径见启动日志；
 *   - 工具来源：MethodToolCallbackProvider 扫描 @Tool 方法 → 注册成 MCP 工具；
 *     也就是说 WeatherTools / DateTimeTools 同时是：
 *       a) ChatClient 对话里的本地工具（ToolCallingController）
 *       b) 标准的 MCP Server 工具（本类）
 *   - 验证方式：用支持 MCP 的客户端（如 Claude Desktop / Cursor）配置指向本服务，
 *     或用 MCP Inspector（npx @modelcontextprotocol/inspector）连接调试。
 *
 * MCP Client（本工程未启用，给出接入姿势）：
 *   1) 引入 spring-ai-starter-mcp-client；
 *   2) application.yml 配置：
 *        spring:
 *          ai:
 *            mcp:
 *              client:
 *                enabled: true
 *                stdio:
 *                  connections:
 *                    my-server:
 *                      command: npx
 *                      args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
 *   3) 客户端自动发现远端工具并注册为 ToolCallbackProvider，
 *      ChatClient 调用时 .toolCallbacks(mcpToolCallbackProvider...) 即可调用远端工具——
 *      对模型来说，本地工具与远程 MCP 工具的使用方式完全一致。
 */
@Configuration
public class McpServerConfig {

    /**
     * 把本地 @Tool 工具对象批量注册为 MCP Server 工具。
     * starter 检测到 ToolCallbackProvider Bean 后会自动接入 MCP 端点，无需其他配置。
     */
    @Bean
    public ToolCallbackProvider saaDemoTools(WeatherTools weatherTools, DateTimeTools dateTimeTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(weatherTools, dateTimeTools)
                .build();
    }
}
