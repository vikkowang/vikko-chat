package com.vikko.chat.chat;

import java.util.List;
import java.util.Objects;

import com.vikko.chat.chat.dto.ChatMessageDto;
import com.vikko.chat.chat.dto.ChatRequest;
import com.vikko.chat.chat.dto.ConversationDto;
import com.vikko.chat.chat.dto.ConversationPageDto;
import com.vikko.chat.mapper.ConversationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class ChatService {

    // 显式 ReAct 循环的 planner(总调度)
    private final Planner planner;
    // 上下文摘要压缩:历史过长时把早期对话摘要掉
    private final ConversationSummarizer summarizer;
    // 由 Spring AI 自动装配:有 JDBC 仓库时持久化到 MySQL,否则退化为内存实现
    private final ChatMemory chatMemory;
    // 直接操作底层仓库,用于列出会话/加载历史(绕开 ChatMemory 的窗口截断)
    private final ChatMemoryRepository chatMemoryRepository;
    // 会话按最近活跃时间倒序
    private final ConversationMapper conversationMapper;

    public ChatService(Planner planner, ConversationSummarizer summarizer, ChatMemory chatMemory,
            ChatMemoryRepository chatMemoryRepository, ConversationMapper conversationMapper) {
        this.planner = planner;
        this.summarizer = summarizer;
        this.chatMemory = chatMemory;
        this.chatMemoryRepository = chatMemoryRepository;
        this.conversationMapper = conversationMapper;
    }

    /**
     * 单轮:每次请求独立,不保留历史上下文。
     */
    public String chat(String message) {
        log.info("单轮对话: {}", message);
        return planner.plan(message);
    }

    /**
     * 多轮:按 conversationId 手动读写 ChatMemory 保留上下文。
     */
    public String chatWithMemory(ChatRequest request) {
        String conversationId = request.getConversationId() == null ? "default" : request.getConversationId();
        log.info("多轮对话 [{}]: {}", conversationId, request.getMessage());
        List<Message> history = chatMemory.get(conversationId);
        String answer = planner.plan(summarizer.compress(history), request.getMessage());
        chatMemory.add(conversationId, List.of(new UserMessage(request.getMessage()), new AssistantMessage(answer)));
        return answer;
    }

    /**
     * 多轮流式:逻辑同 {@link #chatWithMemory(ChatRequest)}。
     * ReAct 循环是多次模型调用的串行过程,无法做到 token 级流式,这里返回单条完整结果。
     */
    public Flux<String> chatWithMemoryStream(ChatRequest request) {
        String conversationId = request.getConversationId() == null ? "default" : request.getConversationId();
        log.info("多轮流式对话 [{}]: {}", conversationId, request.getMessage());
        List<Message> history = chatMemory.get(conversationId);
        String answer = planner.plan(summarizer.compress(history), request.getMessage());
        chatMemory.add(conversationId, List.of(new UserMessage(request.getMessage()), new AssistantMessage(answer)));
        return Flux.just(answer);
    }

    /**
     * 分页列出会话:标题取该会话的第一条用户消息。
     * 多取一条用于判断是否还有下一页,避免额外的 COUNT 查询。
     */
    public ConversationPageDto listConversations(int page, int size) {
        int offset = page * size;
        List<String> ids = conversationMapper.findConversationIdsOrderByRecent(offset, size + 1);
        boolean hasMore = ids.size() > size;
        if (hasMore) {
            ids = ids.subList(0, size);
        }
        List<ConversationDto> items = ids.stream()
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
        return new ConversationPageDto(items, hasMore);
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
