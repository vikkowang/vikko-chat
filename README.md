# vikko-chat

一个 Spring AI 学习项目,聚焦两个核心主题:**Tool Calling(函数调用)** 与 **MCP(Model Context Protocol)**。

MCP 这条线既是 **Server**(把本进程工具暴露出去),也是 **Client**(把外部工具接进来)——后者用钉钉文档 MCP 网关做了实战演示。此外还带持久化多轮记忆和 Web 前端。

## 技术栈

| 组件 | 版本 |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.16 |
| Spring AI | 1.1.8(含 MCP server / client starter) |
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
4. (可选)钉钉文档 MCP 网关地址,已配置在 `application.yml` 的 `app.mcp.dingtalk.url`,见下文「MCP Client」一节。

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
| GET | `/api/chat/conversations` | 会话列表 |
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

`DemoTools` 里的工具会被模型按需调用:

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

会话列表与历史:

```bash
curl http://localhost:8080/api/chat/conversations
curl http://localhost:8080/api/chat/conversations/conv-1
```

### 3. MCP Server(把本进程工具暴露出去)

`DemoTools` 里的 `@Tool` 方法会被 `ToolConfig` 包装成 `ToolCallbackProvider`,**自动**由 MCP Server 经 MCP 协议暴露。用 MCP Inspector 验证:

```bash
npx @modelcontextprotocol/inspector
```

在 Inspector 里选择 Streamable HTTP 传输,地址填 `http://localhost:8080/mcp`,即可看到上述五个工具并调用。

### 4. MCP Client(把外部工具接进来)

本应用同时作为 **MCP 客户端**连接钉钉文档 MCP 网关,把远程工具桥接给 DeepSeek 做函数调用。配置在 `application.yml`:

```yaml
app:
  mcp:
    dingtalk:
      url: https://mcp-gw.dingtalk.com/server/<server-id>?key=<api-key>
```

`McpClientConfig` 用 `McpClient.sync(HttpClientStreamableHttpTransport.builder(...).endpoint(...))` 建立连接,`ChatService` 再用 `SyncMcpToolCallbackProvider` 把远程工具桥接成 `ToolCallback`,通过 `.toolCallbacks(...)` 与本地 `DemoTools` 一起注册。钉钉网关共暴露 **40 个文档工具**(`create_document`、`get_document_content`、`search_documents`、`list_nodes`、`add_permission` 等),覆盖文档/文件夹/知识库的增删改查、权限、版本、导入导出。

可以直接问:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '"列出我最近访问的钉钉文档"'
```

> ⚠️ 两点说明:
> - URL 末尾的 `key` 是访问凭证,建议改成环境变量(如 `${DINGTALK_MCP_URL}`)再注入。
> - 远程工具是**惰性加载**的:应用启动时不会连钉钉,第一次真正触发工具调用时才 `listTools()`;钉钉暂时不可达不影响启动,只影响工具调用。

## 核心概念:函数调用 vs MCP(Server / Client)

同一批「工具」,三种用法,这是本项目想讲清的核心:

- **函数调用(Tool Calling)**:模型在对话过程中,由 `ChatClient` 直接调用本进程内的 `@Tool` 方法(`DemoTools`)。
- **MCP Server**:把本进程工具经标准协议(JSON-RPC + HTTP)暴露给**任意**外部 MCP 客户端(`ToolConfig` + `/mcp` 端点)。
- **MCP Client**:反过来,作为客户端去连**外部** MCP Server,把它的远程工具接进自己的 `ChatClient`(`McpClientConfig` + 钉钉网关)。

一句话:函数调用是「模型直接调」;MCP 是「跨进程/跨服务的协议调用」——Server 是把工具**卖**出去,Client 是把工具**买**进来。

## Roadmap(后续计划)

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| Tool Calling(函数调用) | ✅ 已完成 | `DemoTools` 5 个 `@Tool`,DeepSeek 按需调用 |
| MCP Server | ✅ 已完成 | 把本进程工具经 `/mcp` 暴露给外部客户端 |
| MCP Client | ✅ 已完成 | 连钉钉文档 MCP 网关,40 个远程工具接进对话 |
| 多轮对话记忆 | ✅ 已完成 | `ChatMemory` + JDBC 持久化到 MySQL |
| 流式输出(SSE) | ✅ 已完成 | `/memory/stream` 打字机式返回 |
| Web 前端 | ✅ 已完成 | Vue 3,会话侧边栏 + 流式聊天 |
| RAG(向量检索)⭐ | ⬜ 待做 | Docker + Milvus;切分 / embedding / 检索 / 注入上下文 |
| Multi-Agent 编排 | ⬜ 待做 | 单 agent 自主循环(ReAct)→ 多 agent 协作(planner + workers) |
| 上下文管理 | ⬜ 待做 | 上下文压缩 / 摘要、记忆分层、长对话窗口管理、命中缓存 |
| 结构化输出(JSON Schema) | ⬜ 待做 | 模型按 schema 返回类型化 JSON(agent 与 RAG 的地基) |

⭐ = 重点学习项。

### 待做项拆解

- **RAG(重点)**:Docker 起 Milvus(standalone)→ 配 embedding 模型(本地 ONNX BGE 或托管 `text-embedding-3`)→ `DocumentReader` + `TokenTextSplitter` 切分 → `EmbeddingModel` 向量化 → 写入 `MilvusVectorStore` → 检索 top-k → `QuestionAnswerAdvisor` 注入上下文生成;进阶:混合检索、重排、query 改写。
- **Multi-Agent**:先把 `DemoTools` 交给单个自主 agent(ReAct 多步推理),再用 planner + 专职 worker 做多 agent 协作。
- **上下文管理**:在现有基础记忆之上,做上下文压缩 / 摘要、记忆分层(短期 vs 长期)、长对话窗口管理、prompt 缓存以省 token。
- **结构化输出**:`ChatClient.prompt().entity(...)` + `BeanOutputConverter`,让模型返回受 schema 约束的对象。

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
│   └── McpClientConfig.java        # MCP Client:连接钉钉文档 MCP 网关
├── tool/
│   ├── DemoTools.java              # @Tool 工具集(函数调用 + MCP 共用)
│   └── UserStatus.java             # 账户状态枚举
├── chat/
│   ├── ChatController.java         # /api/chat 系列接口
│   ├── ChatService.java            # ChatClient 装配 + 记忆 + 远程工具桥接
│   └── dto/                        # 请求/响应 DTO(record)
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
