package com.vikko.chat.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "聊天请求")
@Data
public class ChatRequest {

    @Schema(description = "用户消息", example = "北京天气怎么样?")
    private String message;

    @Schema(description = "会话 ID(仅多轮记忆接口使用)", example = "conv-1")
    private String conversationId;
}
