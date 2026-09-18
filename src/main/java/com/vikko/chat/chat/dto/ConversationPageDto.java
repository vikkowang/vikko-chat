package com.vikko.chat.chat.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "会话列表分页结果")
public record ConversationPageDto(
        @Schema(description = "当前页会话") List<ConversationDto> items,
        @Schema(description = "是否还有下一页") boolean hasMore) {
}
