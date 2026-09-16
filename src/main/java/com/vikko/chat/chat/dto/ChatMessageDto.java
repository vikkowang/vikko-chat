package com.vikko.chat.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "对话消息")
public record ChatMessageDto(
        @Schema(description = "角色:user/assistant") String role,
        @Schema(description = "消息内容") String content) {
}
