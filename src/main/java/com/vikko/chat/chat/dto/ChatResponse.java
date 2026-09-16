package com.vikko.chat.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "聊天响应")
public record ChatResponse(
        @Schema(description = "模型回复") String reply) {
}
