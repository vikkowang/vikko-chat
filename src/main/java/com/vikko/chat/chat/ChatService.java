package com.vikko.chat.chat;

import java.util.List;
import java.util.Objects;

import com.vikko.chat.chat.dto.ChatMessageDto;
import com.vikko.chat.chat.dto.ChatRequest;
import com.vikko.chat.chat.dto.ConversationDto;
import com.vikko.chat.mapper.ConversationMapper;
import com.vikko.chat.tool.DemoTools;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final DemoTools demoTools;
    // 钉钉文档 MCP 的远程工具,桥接成 ToolCallbackProvider 供函数调用
    private final SyncMcpToolCallbackProvider dingTalkToolCallbackProvider;
    // 由 Spring AI 自动装配:有 JDBC 仓库时持久化到 MySQL,否则退化为内存实现
    private final ChatMemory chatMemory;
    // 直接操作底层仓库,用于列出会话/加载历史(绕开 ChatMemory 的窗口截断)
    private final ChatMemoryRepository chatMemoryRepository;
    // 会话按最近活跃时间倒序
    private final ConversationMapper conversationMapper;

    public ChatService(ChatClient.Builder chatClientBuilder, DemoTools demoTools, McpSyncClient dingTalkMcpClient,
            ChatMemory chatMemory, ChatMemoryRepository chatMemoryRepository, ConversationMapper conversationMapper) {
        this.chatClient = chatClientBuilder.build();
        this.demoTools = demoTools;
        this.dingTalkToolCallbackProvider = new SyncMcpToolCallbackProvider(dingTalkMcpClient);
        this.chatMemory = chatMemory;
        this.chatMemoryRepository = chatMemoryRepository;
        this.conversationMapper = conversationMapper;
    }

    /**
     * 单轮:每次请求独立,不保留历史上下文。
     * {@code .tools(demoTools).toolCallbacks(dingTalkToolCallbackProvider)} 把工具注册给模型,让 DeepSeek 按需做函数调用。
     */
    public String chat(String message) {
        log.info("单轮对话: {}", message);
        return chatClient.prompt()
                .user(message)
                .tools(demoTools).toolCallbacks(dingTalkToolCallbackProvider)
                .call()
                .content();
    }

    /**
     * 多轮:按 conversationId 用 {@link MessageWindowChatMemory} 保留上下文。
     * {@link MessageChatMemoryAdvisor} 负责把历史消息拼进每次请求;
     * 会话 ID 必须通过 advisor 参数 {@link ChatMemory#CONVERSATION_ID} 每次传入。
     */
    public String chatWithMemory(ChatRequest request) {
        String conversationId = request.getConversationId() == null ? "default" : request.getConversationId();
        log.info("多轮对话 [{}]: {}", conversationId, request.getMessage());
        return chatClient.prompt()
                .user(request.getMessage())
                .advisors(a -> a
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(demoTools).toolCallbacks(dingTalkToolCallbackProvider)
                .call()
                .content();
    }

    /**
     * 多轮流式:和 {@link #chatWithMemory(ChatRequest)} 逻辑一致,只是用 {@code .stream()}
     * 返回 {@link Flux},把模型按 token 生成的片段实时推给前端(SSE)。
     */
    public Flux<String> chatWithMemoryStream(ChatRequest request) {
        String conversationId = request.getConversationId() == null ? "default" : request.getConversationId();
        log.info("多轮流式对话 [{}]: {}", conversationId, request.getMessage());
        return chatClient.prompt()
                .user(request.getMessage())
                .advisors(a -> a
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(demoTools).toolCallbacks(dingTalkToolCallbackProvider)
                .stream()
                .content();
    }

    /**
     * 列出所有会话:标题取该会话的第一条用户消息。
     */
    public List<ConversationDto> listConversations() {
        return conversationMapper.findConversationIdsOrderByRecent().stream()
                .map(id -> {
                    List<Message> messages = chatMemoryRepository.findByConversationId(id);
                    String title = messages.stream()
                            .filter(m -> m.getMessageType() == MessageType.USER)
                            .map(m -> m.getText())
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(id);
                    return new ConversationDto(id, title);
                })
                .toList();
    }

    /**
     * 加载某个会话的历史消息(只返回 user/assistant 两类)。
     */
    public List<ChatMessageDto> getConversation(String conversationId) {
        return chatMemoryRepository.findByConversationId(conversationId).stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .map(m -> new ChatMessageDto(
                        m.getMessageType() == MessageType.USER ? "user" : "assistant",
                        m.getText()))
                .toList();
    }
}
