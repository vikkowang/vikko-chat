package com.vikko.chat.config;

import com.vikko.chat.tool.CalculatorTools;
import com.vikko.chat.tool.UserStatusTools;
import com.vikko.chat.tool.WeatherTimeTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {

    /**
     * 把本地 {@code @Tool} 方法包装成 {@link ToolCallbackProvider}。
     *
     * <p>这个 bean 会被 Spring AI 的 MCP Server 自动装配读取,把工具经 MCP 协议暴露出去。
     * 工具类已按职责拆成 3 个(计算 / 天气时间 / 用户状态),这里一起打包暴露。
     */
    @Bean
    public ToolCallbackProvider demoToolCallbackProvider(CalculatorTools calculatorTools,
            WeatherTimeTools weatherTimeTools, UserStatusTools userStatusTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(calculatorTools, weatherTimeTools, userStatusTools)
                .build();
    }
}
