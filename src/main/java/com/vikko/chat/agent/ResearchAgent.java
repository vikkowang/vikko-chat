package com.vikko.chat.agent;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 检索 agent:一个「严谨的知识库研究员」,内部挂本地 RAG 服务(rag_query)。
 *
 * <p>「agents as tools」:对外暴露一个 {@code @Tool} 方法 research,内部藏一个带人设的 ChatClient。
 */
@Component
public class ResearchAgent implements Agent {

    private final ChatClient client;
    private final ToolCallbackProvider ragProvider;

    public ResearchAgent(ChatClient.Builder builder, McpSyncClient ragMcpClient) {
        this.client = builder.build();
        this.ragProvider = new SyncMcpToolCallbackProvider(ragMcpClient);
    }

    @Tool(description = "检索本地知识库回答专业问题,并附引用来源。适合需要查资料、需要准确出处的场景。")
    public String research(String query) {
        return cap(client.prompt()
                .system("""
                        你是一名严谨的知识库研究员。你的任务是基于检索到的资料回答用户问题。
                        要求:
                        1. 先检索,再基于检索结果作答,不要凭空编造;
                        2. 回答要结构清晰、直接命中问题;
                        3. 资料不足以回答时,明确说「资料中没有相关内容」;
                        4. 结尾列出引用的来源。
                        """)
                .user(query)
                .toolCallbacks(ragProvider)
                .call()
                .content());
    }
}
