package com.vikko.chat.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI springAiLearnOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring AI Learn API")
                        .description("Spring AI 学习项目:tool calling 与 MCP")
                        .version("0.0.1"));
    }
}
