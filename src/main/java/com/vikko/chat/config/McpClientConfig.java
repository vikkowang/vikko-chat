package com.vikko.chat.config;

import java.net.URI;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 客户端配置:作为 client 连接外部 MCP Server。
 *
 * <p>这里注册多个 MCP 客户端,把各外部 server 的远程工具接进函数调用:
 * <ul>
 *   <li>钉钉文档网关({@code app.mcp.dingtalk.url})</li>
 *   <li>本地 RAG 服务({@code app.mcp.rag.url},见 vikko-rag 项目)</li>
 * </ul>
 * 这里只注册 {@link McpSyncClient};桥接成 {@code ToolCallbackProvider} 的动作放在
 * {@code ChatService} 里做,避免被 MCP server 的自动装配
 * {@code ToolCallbackConverterAutoConfiguration} 当成「要暴露的服务器工具」误聚合。
 */
@Configuration
public class McpClientConfig {

    @Bean(destroyMethod = "close")
    public McpSyncClient dingTalkMcpClient(@Value("${app.mcp.dingtalk.url}") String url) {
        return buildMcpClient(url);
    }

    @Bean(destroyMethod = "close")
    public McpSyncClient ragMcpClient(@Value("${app.mcp.rag.url}") String url) {
        return buildMcpClient(url);
    }

    /**
     * 用 URL 建立 Streamable HTTP 的 MCP 客户端。
     *
     * <p>注意:{@code HttpClientStreamableHttpTransport.builder(...)} 的参数是 baseUri,
     * endpoint 默认是 {@code /mcp}。若把完整 URL 当 baseUri 传入,会被 {@code resolve("/mcp")}
     * 覆盖路径、丢掉查询参数(表现为 404)。所以这里拆成「主机 + 路径/query」两段。
     */
    private static McpSyncClient buildMcpClient(String url) {
        URI uri = URI.create(url);
        String baseUri = uri.getScheme() + "://" + uri.getAuthority();
        String endpoint = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
        return McpClient.sync(
                HttpClientStreamableHttpTransport.builder(baseUri)
                        .endpoint(endpoint)
                        .build())
                .build();
    }
}
