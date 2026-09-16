package com.vikko.chat.chat;

import java.util.List;

import com.vikko.chat.chat.dto.ChatMessageDto;
import com.vikko.chat.chat.dto.ChatRequest;
import com.vikko.chat.chat.dto.ChatResponse;
import com.vikko.chat.chat.dto.ConversationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "基于 DeepSeek 的 tool calling 演示")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @Operation(summary = "单轮对话", description = "每次请求独立,模型可按需调用 @Tool 工具;请求体直接传字符串")
    public ChatResponse chat(@RequestBody String message) {
        return new ChatResponse(chatService.chat(message));
    }

    @PostMapping("/memory")
    @Operation(summary = "多轮对话", description = "按 conversationId 保留上下文")
    public ChatResponse chatWithMemory(@RequestBody ChatRequest request) {
        return new ChatResponse(chatService.chatWithMemory(request));
    }

    @PostMapping(value = "/memory/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "多轮流式对话", description = "SSE 流式返回,按 conversationId 保留上下文")
    public Flux<String> chatWithMemoryStream(@RequestBody ChatRequest request) {
        return chatService.chatWithMemoryStream(request);
    }

    @GetMapping("/conversations")
    @Operation(summary = "会话列表", description = "列出所有已持久化的会话(标题取第一条用户消息)")
    public List<ConversationDto> listConversations() {
        return chatService.listConversations();
    }

    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "会话历史", description = "加载某个会话的 user/assistant 消息")
    public List<ChatMessageDto> getConversation(@PathVariable String conversationId) {
        return chatService.getConversation(conversationId);
    }
}
