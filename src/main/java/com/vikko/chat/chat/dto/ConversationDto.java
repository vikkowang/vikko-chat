package com.vikko.chat.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "会话摘要")
public record ConversationDto(
        @Schema(description = "会话 ID") String conversationId,
        @Schema(description = "标题(取第一条用户消息)") String title) {
}
