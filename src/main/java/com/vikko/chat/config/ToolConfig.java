package com.vikko.chat.config;

import com.vikko.chat.tool.DemoTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {

    /**
     * 把 {@link DemoTools} 的 {@code @Tool} 方法包装成 {@link ToolCallbackProvider}。
     *
     * <p>这个 bean 会被 Spring AI 的 MCP Server 自动装配读取,把工具经 MCP 协议暴露出去。
     * 同一个 {@code @Tool} 实现,函数调用走 {@code ChatClient.tools(demoTools)},MCP 走这个 provider。
     */
    @Bean
    public ToolCallbackProvider demoToolCallbackProvider(DemoTools demoTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(demoTools)
                .build();
    }
}
