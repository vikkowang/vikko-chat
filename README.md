# vikko-chat

一个 Spring AI 学习项目,聚焦两个核心主题:**Tool Calling(函数调用)** 与 **MCP(Model Context Protocol)**。

在此基础上扩展成了一个带持久化多轮记忆和 Web 前端的小型聊天应用。

## 技术栈

| 组件 | 版本 |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.16 |
| Spring AI | 1.1.8 |
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

## 快速开始

```bash
# 首次运行会由 Maven Wrapper 自动下载 Maven 与依赖
./mvnw spring-boot:run
```

启动后可访问:

- 聊天前端:http://localhost:8080/
- Swagger UI:http://localhost:8080/swagger-ui.html
- OpenAPI JSON:http://localhost:8080/v3/api-docs
- MCP 端点:http://localhost:8080/mcp

## 接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/chat` | 单轮对话(请求体直接传字符串) |
| POST | `/api/chat/memory` | 多轮对话,按 conversationId 保留上下文 |
| POST | `/api/chat/memory/stream` | 多轮流式对话(SSE) |
| GET | `/api/chat/conversations` | 会话列表 |
| GET | `/api/chat/conversations/{conversationId}` | 加载某会话历史 |

## 三个演示

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

### 3. MCP Server

`DemoTools` 里的 `@Tool` 方法会被 `ToolConfig` 包装成 `ToolCallbackProvider`,**自动**由 MCP Server 经 MCP 协议暴露。用 MCP Inspector 验证:

```bash
npx @modelcontextprotocol/inspector
```

在 Inspector 里选择 Streamable HTTP 传输,地址填 `http://localhost:8080/mcp`,即可看到上述五个工具并调用。

## 核心概念:函数调用 vs MCP

- **函数调用(Tool Calling)**:模型在对话过程中,由 `ChatClient` 直接调用本进程内 `@Tool` 方法。
- **MCP**:把工具通过标准协议(JSON-RPC + HTTP)暴露给**任意** MCP 客户端。工具先被
  `ToolConfig` 包装成 `ToolCallbackProvider`,再由 MCP Server 暴露。

同一份 `@Tool` 实现,两条路:函数调用是「模型直接调」,MCP 是「外部客户端通过协议调」。

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
│   └── ToolConfig.java             # 把 @Tool 包装成 ToolCallbackProvider(供 MCP 用)
├── tool/
│   ├── DemoTools.java              # @Tool 工具集(函数调用 + MCP 共用)
│   └── UserStatus.java             # 账户状态枚举
├── chat/
│   ├── ChatController.java         # /api/chat 系列接口
│   ├── ChatService.java            # ChatClient 装配 + 记忆
│   └── dto/                        # 请求/响应 DTO(record)
├── mapper/
│   ├── ConversationMapper.java     # 会话列表(按最近活跃排序)
│   └── UserStatusMapper.java       # 查/改 user_status 表
└── exception/                      # 全局异常处理 + 统一错误结构

src/main/resources/
├── application.yml                 # 数据源 / DeepSeek / MCP 配置
├── schema.sql / data.sql           # user_status 建表 + 示例数据
└── static/                         # 前端构建产物

frontend/                           # Vue 前端源码
```

## 常见问题

- **启动报 `Could not resolve placeholder 'DEEPSEEK_API_KEY'`**:没设置环境变量,先 `export DEEPSEEK_API_KEY=sk-xxx`。
- **启动报数据库连接失败**:确认 MySQL 已启动、`test` 库存在、`application.yml` 里的账号密码正确。
- **MCP Inspector 连不上**:确认应用已启动,且地址用 `http://localhost:8080/mcp`、传输选 Streamable HTTP。
