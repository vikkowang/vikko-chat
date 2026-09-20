# vikko-chat

一个 Spring AI 学习项目,聚焦两个核心主题:**Tool Calling(函数调用)** 与 **MCP(Model Context Protocol)**。

MCP 这条线既是 **Server**(把本进程工具暴露出去),也是 **Client**(把外部工具接进来)——后者用钉钉文档 MCP 网关和本地 RAG 服务做了实战演示。此外还带持久化多轮记忆、RAG 检索增强和 Web 前端。

## Roadmap(进度总览)

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| Spring AI 脚手架 | ✅ | Maven + Spring Boot + Spring AI 起步,含 DeepSeek 接入、MySQL/MyBatis、全局异常、Swagger |
| Tool Calling(函数调用) | ✅ | 本地 5 个 `@Tool`(计算/天气时间/用户状态),DeepSeek 按需调用 |
| MCP Server / Client ⭐ | ✅ | Server 暴露本地工具;Client 接钉钉(40 工具)+ 本地 RAG |
| 多轮记忆 + 流式 + 前端 | ✅ | `ChatMemory` + JDBC、SSE、Vue 3 |
| RAG(基础)⭐ | ✅ | vikko-rag:本地 BGE + milvus-lite + DeepSeek,MCP 暴露 |
| Multi-Agent 编排 ⭐ | ✅ | planner + 5 个专职 agent(检索/钉钉/计算/天气时间/用户管理),agents-as-tools |
| ReAct 循环 ⭐ | ✅ | 显式「思考→行动→观察」循环,每步 Action/Observation 打日志,替换 ChatClient 隐式循环 |
| LangGraph4j 编排 | ✅ | 用 StateGraph 两节点图表达同一个 ReAct 循环,与手写版经 `app.planner.mode` 切换 |
| Advisor 管道 | ✅ | ChatClient + advisor 链(蜜罐/安全/摘要记忆/日志/幻觉/脱敏),第三种编排实现 |
| 上下文管理 ⭐ | 🟡 部分 | 摘要压缩已做;长期记忆 / 增量摘要 / 窗口截断待做 |
| RAG 进阶 ⭐ | ✅ | 重排 / 混合检索 / 查询改写 / 切分优化 / 定时拉新(vikko-rag) |
| 质量与幻觉治理 | 🟡 部分 | 幻觉检测(浅版,GroundednessAdvisor)已做;事实核查 / 护栏待做 |
| 评测(Eval) | ⬜ | golden 断言 → RAG faithfulness → LLM-as-judge → CI 回归 |
| 结构化输出 | ⬜ | JSON Schema 约束的返回 |
| 工程化 | ⬜ | 可观测 / 模型路由 / 成本控制 |

⭐ = 重点。

### 待做项拆解

#### RAG 进阶

- **检索优化**:混合检索(BM25 + 向量双路召回)、重排(rerank 二阶段精排)、查询改写(query rewrite / HyDE)。
- **切分优化(chunk)**:语义切分代替固定字符数,块大小 / 重叠调优。
- **数据刷新**:定时拉新文档、增量入库、过期文档清理。

#### 质量与幻觉治理

- **幻觉检测 / 护栏**:groundedness 校验——答案无检索资料支撑就拦截或降级。
- **事实核查**:把答案里的论断反向核对检索结果,标出「无出处」的部分。

#### 评测(Eval)

- 分层落地:① 确定性工具 + 工具调用轨迹的 golden 断言(免费、快、硬)→ ② RAG faithfulness(防幻觉,用 Ragas)→ ③ LLM-as-judge 兜底开放答案。
- 评测集用 YAML,Python 黑盒 harness 打 `/api/chat` 接口,进 CI 做回归。

#### 上下文管理进阶

- **长期记忆 / 记忆分层**:补上「写路径」——从对话抽取跨会话事实存独立表,按相关度检索注入(而不是全量摘要)。
- **增量摘要**:把当前「每轮从头重摘要」改成「旧摘要 + 新增消息」增量更新。
- **窗口截断**:用 `LastMaxTokenSizeContentPurger` 做硬上限兜底。

#### 工程化

- 可观测性(tracing / metrics)、模型路由(简单任务走小模型)、成本控制(prompt 缓存 + token 预算)。

#### 结构化输出

- `ChatClient.prompt().entity(...)` + `BeanOutputConverter`,让模型返回受 schema 约束的对象。

## 技术栈

| 组件 | 版本 |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.16 |
| Spring AI | 1.1.8(含 MCP server / client starter) |
| LangGraph4j | 1.9.0(图编排,ReAct 的框架化实现) |
| LLM | DeepSeek(`deepseek-chat`) |
| 数据库 | MySQL(JDBC + MyBatis 3.0.5) |
| 接口文档 | springdoc-openapi 2.8.17(Swagger UI) |
| 前端 | Vue 3 + Vite(markdown-it + highlight.js) |
| 其它 | Lombok、Maven Wrapper |

## 前置条件

1. JDK 21(本机已装)。
2. MySQL 已启动,且存在 `test` 数据库。连接信息见 `src/main/resources/application.yml`(可按需修改)。启动时会自动建表并写入示例数据。
3. DeepSeek API Key(去 platform.deepseek.com 申请),通过环境变量提供:
   ```bash
   export DEEPSEEK_API_KEY=sk-xxx
   ```
4. (可选)两个外部 MCP Server,已配置在 `application.yml`:
   - 钉钉文档 MCP 网关:`app.mcp.dingtalk.url`
   - 本地 RAG 服务:`app.mcp.rag.url`(即 `vikko-rag` 项目,需单独启动)

## 快速开始

```bash
# 首次运行会由 Maven Wrapper 自动下载 Maven 与依赖
./mvnw spring-boot:run
```

启动后可访问:

- 聊天前端:http://localhost:8080/
- Swagger UI:http://localhost:8080/swagger-ui.html
- OpenAPI JSON:http://localhost:8080/v3/api-docs
- MCP 端点(本应用作为 Server):http://localhost:8080/mcp

## 接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/chat` | 单轮对话(请求体直接传字符串) |
| POST | `/api/chat/memory` | 多轮对话,按 conversationId 保留上下文 |
| POST | `/api/chat/memory/stream` | 多轮流式对话(SSE) |
| GET | `/api/chat/conversations?page=0&size=20` | 会话列表(分页,返回 `{ items, hasMore }`) |
| GET | `/api/chat/conversations/{conversationId}` | 加载某会话历史 |

## 四个演示

### 1. Tool Calling(函数调用)

`POST /api/chat` —— 单轮,模型按需调用 `@Tool` 工具:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '"帮我算一下 12 * 8 等于多少"'

curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '"查一下 alice 的账户状态"'
```

本地 `@Tool` 工具(按职责拆成计算 / 天气时间 / 用户状态三类)会被模型按需调用:

| 工具 | 说明 |
| --- | --- |
| `calculate(a, b, operation)` | 四则运算 |
| `getWeather(city)` | 模拟天气 |
| `getCurrentTime(zone)` | 指定 IANA 时区的当前时间 |
| `queryUserStatus(username)` | 查询用户账户状态(查 MySQL) |
| `updateUserStatus(username, status)` | 修改用户账户状态(改 MySQL) |

其中后两个工具走 MyBatis 操作 `user_status` 表(启动时由 `schema.sql` / `data.sql` 建表,并写入 alice / bob / carol 三条示例数据),是「工具调用 + 数据库」的完整闭环演示。

### 2. 多轮对话(带记忆)

`POST /api/chat/memory` —— 按 `conversationId` 保留上下文:

```bash
curl -X POST http://localhost:8080/api/chat/memory \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"conv-1","message":"我叫小明"}'

curl -X POST http://localhost:8080/api/chat/memory \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"conv-1","message":"我叫什么?"}'
```

第二个请求能答出「小明」,说明上下文被记住了。历史经 `ChatMemory` 持久化到 MySQL 的 `SPRING_AI_CHAT_MEMORY` 表(Spring AI 自动建表)。

`POST /api/chat/memory/stream` 逻辑相同,只是以 SSE 流式返回,前端据此打字机式渲染。

会话列表(分页:`?page=0&size=20`,返回 `{ items, hasMore }`)与历史:

```bash
curl 'http://localhost:8080/api/chat/conversations?page=0&size=20'
curl http://localhost:8080/api/chat/conversations/conv-1
```

### 3. MCP Server(把本进程工具暴露出去)

本地 `@Tool` 方法会被 `ToolConfig` 包装成 `ToolCallbackProvider`,**自动**由 MCP Server 经 MCP 协议暴露。用 MCP Inspector 验证:

```bash
npx @modelcontextprotocol/inspector
```

在 Inspector 里选择 Streamable HTTP 传输,地址填 `http://localhost:8080/mcp`,即可看到上述五个工具并调用。

### 4. MCP Client(把外部工具接进来)

本应用同时作为 **MCP 客户端**连接两个外部 MCP Server,把远程工具桥接给 DeepSeek 做函数调用。配置在 `application.yml`:

```yaml
app:
  mcp:
    dingtalk:
      url: https://mcp-gw.dingtalk.com/server/<server-id>?key=<api-key>
    rag:
      url: http://127.0.0.1:9000/mcp   # 本地 RAG 服务(vikko-rag)
```

`McpClientConfig` 用 `McpClient.sync(HttpClientStreamableHttpTransport.builder(...).endpoint(...))` 建立两个 `McpSyncClient`,分别交给「检索 agent」和「钉钉 agent」,由它们内部用 `SyncMcpToolCallbackProvider` 桥接成 `ToolCallback`。两个外部 Server 各提供:

- **钉钉文档网关**:40 个文档工具(`create_document`、`get_document_content`、`search_documents`、`list_nodes`、`add_permission` 等),覆盖文档/文件夹/知识库的增删改查、权限、版本、导入导出。
- **本地 RAG 服务**(`vikko-rag`):一个 `rag_query` 工具,基于本地 BGE + milvus-lite 检索知识库,再用 DeepSeek 生成带引用的回答(详见 `vikko-rag` 项目)。

可以直接问:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '"列出我最近访问的钉钉文档"'

curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '"用本地知识库查一下 RSI 是什么"'
```

> ⚠️ 说明:
> - 远程工具是**惰性加载**的:应用启动时不会连外部 server,第一次真正触发工具调用时才 `listTools()`;外部 server 暂时不可达不影响启动,只影响工具调用。
> - 钉钉 URL 末尾的 `key` 是访问凭证,建议改成环境变量再注入。
> - RAG 服务是独立的 Python 进程,需先启动(见 `vikko-rag` 的 README)。

### 5. Multi-Agent(多 agent 协作)

`ChatService` 现在是一个 **planner(主 agent)**,它不再直接挂零散工具,而是挂 5 个**专职子 agent**,通过「agents as tools」模式委派任务:

| 子 agent | 人设 | 挂的工具 |
| --- | --- | --- |
| 检索 agent | 知识库研究员 | `rag_query`(RAG MCP) |
| 钉钉 agent | 钉钉文档助手 | 40 个钉钉工具(钉钉 MCP) |
| 计算 agent | 数学助手 | `calculate` |
| 天气时间 agent | 生活助手 | `getWeather` + `getCurrentTime` |
| 用户管理 agent | 账户管理员 | `queryUserStatus` + `updateUserStatus` |

每个子 agent 内部有自己的 ChatClient + 专属工具 + 人设系统提示词;planner 把任务委派给它们,由它们各自规划执行、返回结果。典型的多步例子:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '"12 乘 8 再加 5"'
```

这个复合算式会让「计算 agent」分步调用 calculate 工具。核心区别:工具只会「执行一次」,agent 能「自己规划做几步」。

planner 本身有**三种编排实现**,通过 `application.yml` 的 `app.planner.mode`(环境变量 `PLANNER_MODE`)切换,默认 `advisor`:

- `react`:手写 ReAct 循环(`ReActPlanner`);
- `langgraph`:LangGraph4j 两节点图(`LangGraphPlanner`);
- `advisor`:ChatClient + advisor 链(`AdvisorPlanner`)。

三者共用同一套 5 个子 agent + 系统提示词,由 `PlannerFactory` 多选一(见 `orchestrator/` 包)。`advisor` 模式的记忆由 `SummarizingMemoryAdvisor` 自动读写(含摘要压缩),其余两种仍由 `ChatService` 手动管理。

`advisor` 模式的 advisor 链(按执行顺序,共 6 个):

| advisor | 阶段 | 作用 |
| --- | --- | --- |
| `HoneypotAdvisor` | before / after | 蜜罐对抗:埋 token,检测提示注入泄露 |
| `SafeGuardAdvisor` | before | 输入敏感词拦截(命中 `app.guard.sensitive-words` 即拒答) |
| `SummarizingMemoryAdvisor` | before / after | 加载历史 + 超长历史摘要压缩(替代内置 `MessageChatMemoryAdvisor`) |
| `SimpleLoggerAdvisor` | before / after | 请求 / 响应日志 |
| `GroundednessAdvisor` | after | 幻觉检测(浅版):无出处回答追加「请核实」警示 |
| `SensitiveDataAdvisor` | after | 输出脱敏:正则打码 PII |

## 核心概念:函数调用 vs MCP(Server / Client)

同一批「工具」,三种用法,这是本项目想讲清的核心:

- **函数调用(Tool Calling)**:模型在对话过程中,由 `ChatClient` 直接调用本进程内的 `@Tool` 方法(计算 / 天气时间 / 用户状态)。
- **MCP Server**:把本进程工具经标准协议(JSON-RPC + HTTP)暴露给**任意**外部 MCP 客户端(`ToolConfig` + `/mcp` 端点)。
- **MCP Client**:反过来,作为客户端去连**外部** MCP Server,把它的远程工具接进自己的 `ChatClient`(`McpClientConfig` + 钉钉网关 / 本地 RAG 服务)。

一句话:函数调用是「模型直接调」;MCP 是「跨进程/跨服务的协议调用」——Server 是把工具**卖**出去,Client 是把工具**买**进来。

## 前端

前端是 Vue 3 + Vite 单页应用(`frontend/`),功能:会话侧边栏、SSE 流式聊天、Markdown / 代码高亮渲染。

```bash
cd frontend
npm install
npm run dev      # 开发:http://localhost:5173,/api 代理到 8080
npm run build    # 构建:产物输出到 src/main/resources/static,随 jar 一起运行
```

## 目录结构

```
src/main/java/com/vikko/chat/
├── SpringAiLearnApplication.java   # 启动类
├── config/
│   ├── OpenApiConfig.java          # Swagger 标题/版本
│   ├── ToolConfig.java             # MCP Server:把 @Tool 包装成 ToolCallbackProvider
│   └── McpClientConfig.java        # MCP Client:连接钉钉文档 + 本地 RAG MCP
├── agent/
│   ├── ResearchAgent.java          # 检索 agent(挂 RAG)
│   ├── DingTalkAgent.java          # 钉钉 agent(挂 40 个钉钉工具)
│   ├── CalculatorAgent.java        # 计算 agent
│   ├── WeatherTimeAgent.java       # 天气/时间 agent
│   └── UserAdminAgent.java         # 用户管理 agent
├── tool/
│   ├── CalculatorTools.java        # calculate
│   ├── WeatherTimeTools.java       # getWeather / getCurrentTime
│   ├── UserStatusTools.java        # query / update user status
│   └── UserStatus.java             # 账户状态枚举
├── chat/
│   ├── ChatController.java         # /api/chat 系列接口
│   ├── ChatService.java            # 总调度入口:挂编排器 + 记忆 + 摘要压缩
│   └── dto/                        # 请求/响应 DTO(record)
├── orchestrator/
│   ├── AgentOrchestrator.java      # 编排抽象接口(共用系统提示词)
│   ├── PlannerFactory.java         # 按 app.planner.mode 选编排器
│   ├── ConversationSummarizer.java # 上下文摘要压缩
│   ├── planner/
│   │   ├── ReActPlanner.java       # 手写 ReAct 循环实现
│   │   ├── LangGraphPlanner.java   # LangGraph4j 两节点图实现
│   │   └── AdvisorPlanner.java     # ChatClient + advisor 链实现(挂 6 个 advisor)
│   └── advisor/
│       ├── HoneypotAdvisor.java          # 蜜罐对抗 advisor(提示注入检测)
│       ├── SummarizingMemoryAdvisor.java # 带摘要压缩的记忆 advisor
│       ├── GroundednessAdvisor.java      # 幻觉检测 advisor(浅版)
│       └── SensitiveDataAdvisor.java     # 内容脱敏 advisor(输出正则打码)
├── mapper/
│   ├── ConversationMapper.java     # 会话列表(按最近活跃排序)
│   └── UserStatusMapper.java       # 查/改 user_status 表
└── exception/                      # 全局异常处理 + 统一错误结构

src/main/resources/
├── application.yml                 # 数据源 / DeepSeek / MCP(server + client)配置
├── schema.sql / data.sql           # user_status 建表 + 示例数据
└── static/                         # 前端构建产物

frontend/                           # Vue 前端源码
```

## 常见问题

- **启动报 `Could not resolve placeholder 'DEEPSEEK_API_KEY'`**:没设置环境变量,先 `export DEEPSEEK_API_KEY=sk-xxx`。
- **启动报数据库连接失败**:确认 MySQL 已启动、`test` 库存在、`application.yml` 里的账号密码正确。
- **MCP Inspector 连不上**:确认应用已启动,且地址用 `http://localhost:8080/mcp`、传输选 Streamable HTTP。
- **连钉钉 MCP 报 404 `Server Not Found`**:`HttpClientStreamableHttpTransport.builder(...)` 的参数是 baseUri,endpoint 默认 `/mcp`;若把完整 URL(含路径和 `?key=`)整个传进去,会被 `resolve("/mcp")` 覆盖路径、丢掉查询参数。需要把「主机」和「路径 + query」拆成两段传给 `.endpoint(...)`(见 `McpClientConfig`)。
