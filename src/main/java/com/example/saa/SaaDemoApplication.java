package com.example.saa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ============================================================================
 * 【知识点 0】框架定位：Spring AI 与 Spring AI Alibaba 是什么关系？
 * ============================================================================
 *
 * 一句话版本：
 *   Spring AI     = Spring 官方的 AI 应用统一抽象层（类似 JDBC 之于各种数据库）。
 *   Spring AI Alibaba（SAA）= 阿里云在 Spring AI 之上的增强实现 + Agent 框架。
 *
 * 分层理解（从下到上）：
 *
 *   第 3 层：Agent 框架层（SAA 独有增强）
 *           ReactAgent、Graph 工作流编排（StateGraph）、多智能体（Supervisor/Routing）、
 *           Hooks、Skills、Studio 调试可视化 —— 这层解决"怎么组织 Agent 协作"的问题。
 *
 *   第 2 层：模型接入与生态适配层（SAA 的主要工作）
 *           DashScope（通义千问）的 ChatModel / EmbeddingModel 实现、Nacos 配置管理、
 *           A2A 通信、各类工具调用与文档读取的 starter —— 这层解决"用哪家模型/服务"的问题。
 *
 *   第 1 层：Spring AI 核心抽象层（Spring 官方）
 *           ChatModel / ChatClient / Tool Calling / Memory / Advisor / RAG 抽象 / MCP 等
 *           统一 API —— 这层解决"屏蔽不同模型差异"的问题。
 *
 * 关键结论：SAA 完全兼容 Spring AI 的 API 标准。你写的 ChatClient、Advisor、@Tool
 * 代码在 SAA 与其他 Spring AI 实现之间可以平移，换模型通常只需要换 starter 和配置。
 *
 * ============================================================================
 * 【知识点清单 · 导览】每个知识点对应一个类/接口，README.md 里有完整学习路线：
 * ----------------------------------------------------------------------------
 *   知识点 1  ChatModel 与 ChatClient     → controller/BasicChatController
 *   知识点 2  DashScope 接入              → application.yml + BasicChatController
 *   知识点 3  流式输出（Streaming）        → controller/StreamController
 *   知识点 4  结构化输出（Structured）     → controller/StructuredOutputController
 *   知识点 5  Tool Calling（工具调用）     → tools/ + controller/ToolCallingController
 *   知识点 6  Memory（会话记忆）+ Advisor  → config/MemoryConfig + controller/MemoryAdvisorController
 *   知识点 7  RAG（检索增强生成）          → config/RagConfig + controller/RagController
 *   知识点 8  MCP（模型上下文协议）        → mcp/McpServerConfig
 *   知识点 9  Graph 工作流 / 多智能体      → graph/GraphConfig + graph/GraphController
 * ============================================================================
 */
@SpringBootApplication
public class SaaDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaaDemoApplication.class, args);
    }
}
