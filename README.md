# Spring AI Alibaba 核心知识点学习 Demo

一个**可以跑起来**的 Spring AI Alibaba（1.1.2.0）学习工程：每个 HTTP 接口对应一个知识点，代码里带逐点中文讲解注释，适合边跑边学。

> 版本对齐（官方推荐，来源：[SAA 版本说明](https://agentic-spring-ai.github.io/website/docs/versions/)）
>
> | 组件 | 版本 |
> |---|---|
> | Spring AI Alibaba | 1.1.2.0 |
> | Spring AI | 1.1.2 |
> | Spring Boot | 3.5.6 |
> | Java | 17+ |

---

## 知识点地图

| # | 知识点 | 接口 | 源码入口 |
|---|---|---|---|
| 0 | 框架定位（SAA 与 Spring AI 的关系） | — | `SaaDemoApplication.java` 类头注释 |
| 1 | ChatModel vs ChatClient、PromptTemplate | `GET /ai/chat-model` `GET /ai/chat-client` | `controller/BasicChatController` |
| 2 | DashScope（百炼）接入 | 所有接口共用 | `application.yml` + 各 Controller |
| 3 | 流式输出（SSE / Flux） | `GET /ai/stream` | `controller/StreamController` |
| 4 | 结构化输出（entity） | `GET /ai/struct` | `controller/StructuredOutputController` |
| 5 | Tool Calling（@Tool） | `GET /ai/tool` | `tools/*` + `controller/ToolCallingController` |
| 6 | Memory 会话记忆 + Advisor 拦截器 | `GET /ai/memory/chat` | `config/MemoryConfig` + `controller/MemoryAdvisorController` |
| 7 | RAG 检索增强 | `GET /ai/rag` | `config/RagConfig` + `controller/RagController` |
| 8 | MCP（Server 端 + Client 接入姿势） | MCP 端点（见启动日志） | `mcp/McpServerConfig` |
| 9 | Graph 工作流 / 条件路由（多智能体基础） | `GET /agent/graph/run` | `graph/GraphConfig` + `graph/GraphController` |
| 10 | 动态工具池（统一注册 / 分组 / 按需挂载 / 运行时摘挂） | `GET /ai/tool-pool` | `tools/pool/*` + `config/ToolPoolConfig` + `controller/ToolPoolController` |
| 11 | 并发工具调用（时序观察 / 竞态反例正例 / 每会话一实例） | `GET /ai/tool-parallel` `GET /ai/tool-race` `GET /ai/tool-session` | `tools/ConcurrencyDemoTools` + `config/SessionToolRegistry` + `controller/ConcurrencyToolController` |

---

## 快速开始

### 1. 准备 API Key（必须）

1. 访问[阿里云百炼控制台](https://bailian.console.aliyun.com)，开通模型服务并创建 API Key；
2. 设置环境变量（**不要把 key 写进代码或配置文件**）：

```bash
# Linux / macOS
export DASHSCOPE_API_KEY=sk-xxxxxxxx

# Windows CMD
set DASHSCOPE_API_KEY=sk-xxxxxxxx

# Windows PowerShell
$env:DASHSCOPE_API_KEY="sk-xxxxxxxx"
```

> 没配 key 应用也能启动（工程里预置了占位默认值），但调用任何模型接口都会返回鉴权错误；`/ai/rag` 的启动建库也会失败并在日志中给出 warning，不影响其余接口的 Spring 装配。

### 2. 启动

```bash
# 环境要求：JDK 17+、Maven 3.8+
mvn spring-boot:run

# 或先打包再运行
mvn clean package -DskipTests
java -jar target/spring-ai-alibaba-demo-1.0.0.jar
```

### 3. 逐个接口玩一遍（建议按知识点顺序）

```bash
# 知识点 1：两种调用方式
curl "http://localhost:8080/ai/chat-model?message=一句话介绍Spring AI Alibaba"
curl "http://localhost:8080/ai/chat-client?topic=Spring AI Alibaba&name=振海"

# 知识点 3：流式输出（-N 关闭缓冲，观察打字机效果）
curl -N "http://localhost:8080/ai/stream?message=用三段话解释什么是RAG"

# 知识点 4：结构化输出（返回 JSON 对象而非一段话）
curl "http://localhost:8080/ai/struct?preference=想找一部关于人工智能的科幻片"

# 知识点 5：工具调用（看控制台 DEBUG 日志观察 tool_call 循环）
curl "http://localhost:8080/ai/tool?question=广州今天天气怎么样？我该穿什么？"

# 知识点 6：会话记忆（同一 conversationId 连问两次，第二次记得你）
curl "http://localhost:8080/ai/memory/chat?conversationId=u001&message=我叫振海，9年Java开发"
curl "http://localhost:8080/ai/memory/chat?conversationId=u001&message=我叫什么？做什么工作的？"
# 换 conversationId=u002 再问同样的问题——答不上来，这就是会话隔离

# 知识点 7：RAG（答案在 src/main/resources/docs/ 的知识文档里）
curl "http://localhost:8080/ai/rag?question=Advisor在Spring AI里是什么角色？"

# 知识点 9：Graph 工作流（问题会被分类路由到不同节点）
curl "http://localhost:8080/agent/graph/run?query=什么是Spring AI的Advisor机制？"
curl "http://localhost:8080/agent/graph/run?query=怎么用Spring AI写一个流式接口？"

# 知识点 10：动态工具池（先看池子全貌，再做"挂载子集"对照实验）
curl "http://localhost:8080/ai/tool-pool/status"
curl "http://localhost:8080/ai/tool-pool?groups=travel&question=广州去上海有什么航班？顺便推荐个酒店"
curl "http://localhost:8080/ai/tool-pool?groups=travel&question=美元兑人民币汇率是多少？"   # 只挂了 travel，模型没有汇率工具，只能坦白
curl "http://localhost:8080/ai/tool-pool?groups=travel,finance&question=美元兑人民币汇率是多少？"   # 挂上 finance 就能查了
# 运行时摘挂（不重启）：下游故障时先把相关工具摘掉止血
curl "http://localhost:8080/ai/tool-pool/toggle?group=finance&enabled=false"
curl "http://localhost:8080/ai/tool-pool/toggle?group=finance&enabled=true"

# 知识点 11：并发工具调用（三个实验按顺序玩）
# 1) 模型一次返回多个 tool_call 的执行时序：看返回值末尾的时间线（1.1.2 是串行逐个执行）
curl "http://localhost:8080/ai/tool-parallel"
# 2) 竞态压测：8 线程 × 10000 次直呼工具方法，看不线程安全的计数器怎么"对不上账"
curl "http://localhost:8080/ai/tool-race"
curl "http://localhost:8080/ai/tool-race?threads=16&loops=20000"
# 3) 会话隔离：u1 记的笔记 u2 看不到（每会话一实例）
curl "http://localhost:8080/ai/tool-session?conversationId=u1&message=记一条笔记：明天上午开评审会"
curl "http://localhost:8080/ai/tool-session?conversationId=u1&message=我记过哪些笔记？"
curl "http://localhost:8080/ai/tool-session?conversationId=u2&message=我记过哪些笔记？"
```

### 4. 验证 MCP Server（知识点 8）

应用启动后内嵌了一个 MCP Server，把 `WeatherTools`、`DateTimeTools` 两个工具以标准 MCP 协议暴露。端点地址见启动日志。可以用以下任一客户端连接验证：

- [MCP Inspector](https://github.com/modelcontextprotocol/inspector)：`npx @modelcontextprotocol/inspector`
- Claude Desktop / Cursor 等 MCP 客户端，把服务地址指到本应用端口

同一个 `@Tool` 方法既是 ChatClient 的本地工具，又是 MCP Server 的远程工具——这就是抽象层的复用价值。

---

## 项目结构

```
src/main/java/com/example/saa/
├── SaaDemoApplication.java        # 知识点 0：框架定位总览（先读这个类头注释）
├── controller/
│   ├── BasicChatController.java       # 知识点 1/2：ChatModel、ChatClient、DashScope
│   ├── StreamController.java          # 知识点 3：流式输出
│   ├── StructuredOutputController.java# 知识点 4：结构化输出
│   ├── ToolCallingController.java     # 知识点 5：工具调用注册与观察
│   ├── MemoryAdvisorController.java   # 知识点 6：记忆 + Advisor
│   ├── RagController.java             # 知识点 7：RAG 查询
│   ├── ToolPoolController.java        # 知识点 10：工具池按需挂载与运行时摘挂
│   └── ConcurrencyToolController.java # 知识点 11：并发工具调用三实验
├── config/
│   ├── MemoryConfig.java              # 知识点 6：记忆三层结构
│   ├── RagConfig.java                 # 知识点 7：RAG 建库流水线
│   ├── ToolPoolConfig.java            # 知识点 10：工具池（注册/分组/开关）
│   └── SessionToolRegistry.java       # 知识点 11：有状态工具每会话一实例
├── tools/
│   ├── WeatherTools.java              # 知识点 5：@Tool 工具定义
│   ├── DateTimeTools.java
│   ├── ConcurrencyDemoTools.java      # 知识点 11：反例/正例计数器 + 慢工具 + 幂等工具
│   └── pool/
│       ├── FlightTools.java           # 知识点 10：池内工具（travel 组）
│       ├── HotelTools.java            # 知识点 10：池内工具（travel 组）
│       └── ExchangeRateTools.java     # 知识点 10：池内工具（finance 组）
├── mcp/
│   └── McpServerConfig.java           # 知识点 8：MCP Server + Client 接入姿势
└── graph/
    ├── GraphConfig.java               # 知识点 9：StateGraph 状态图定义
    └── GraphController.java           # 知识点 9：编译与执行
src/main/resources/
├── application.yml                    # DashScope / MCP / 日志配置（重点注释）
└── docs/                              # RAG 知识库文档（启动时自动向量化入库）
```

## 建议学习路线（对应 4 周 Dify→代码路径）

- **第 1 步（概念打底）**：读 `SaaDemoApplication` 类头注释 → 跑通 `/ai/chat-model`、`/ai/chat-client`，理解 ChatModel/ChatClient 分层与 DashScope 接入。
- **第 2 步（单轮能力）**：`/ai/stream`、`/ai/struct`、`/ai/tool` —— 流式、结构化、工具调用是所有 Agent 的地基；重点读 `WeatherTools` 的注释理解 tool_call 循环。
- **第 3 步（上下文工程）**：`/ai/memory/chat` + `/ai/rag` —— 理解 Advisor 管道模型，它把记忆与 RAG 统一成可插拔的横切能力；这对应 Dify 里的"会话变量 + 知识库"。
- **第 4 步（编排进阶）**：`/agent/graph/run` —— 从 ChatClient 单次调用升级到 Graph 状态图编排；理解条件边后，多智能体（Supervisor/Routing）就是在图上组合多个 Agent 节点。

## 常见问题

- **401 / InvalidApiKey**：检查环境变量 `DASHSCOPE_API_KEY` 是否对当前 shell 生效，key 是否与百炼账号匹配。
- **RAG 启动报 warning**：没配 key 时建库失败属预期，接口报错只影响 `/ai/rag`。
- **依赖下载慢/失败**：pom 里已内置阿里云公共仓库；海外环境可删除 `<repositories>` 段。
- **想换模型**：改 `application.yml` 里的 `model`（qwen-turbo 便宜 / qwen-max 效果好）；要换厂商就换对应 starter，业务代码不用动。

## 参考文档

- [Spring AI Alibaba 官方文档](https://java2ai.com) / [版本说明](https://agentic-spring-ai.github.io/website/docs/versions/)
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/)
- [官方示例仓库](https://github.com/alibaba/spring-ai-alibaba/tree/main/examples)
