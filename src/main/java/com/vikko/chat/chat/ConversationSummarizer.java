package com.vikko.chat.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * 上下文摘要压缩(上下文管理)。
 *
 * <p>多轮对话的历史会无限增长,而模型的上下文窗口和 token 预算都有限。
 * 这里做「摘要压缩」:当历史消息超过阈值时,把早期对话总结成一段摘要,
 * 之后只把「摘要 + 最近几条原文」发给模型——既省 token,又尽量保留关键信息。
 */
@Slf4j
@Component
public class ConversationSummarizer {

    /** 历史消息超过这个条数就触发压缩。 */
    private static final int MAX_HISTORY = 10;
    /** 压缩后保留最近多少条原文(其余的被摘要掉)。 */
    private static final int KEEP_RECENT = 4;

    private final ChatModel chatModel;

    public ConversationSummarizer(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 压缩历史。未超阈值原样返回;否则返回「摘要 + 最近 KEEP_RECENT 条原文」。
     *
     * @param history 完整历史(不含当前这条用户消息)
     * @return 压缩后的历史,可直接交给 planner
     */
    public List<Message> compress(List<Message> history) {
        if (history.size() <= MAX_HISTORY) {
            return history;
        }
        int splitAt = history.size() - KEEP_RECENT;
        List<Message> oldPart = history.subList(0, splitAt);
        List<Message> recent = history.subList(splitAt, history.size());

        String summary = summarize(oldPart);
        log.info("上下文压缩:{} 条历史 → 摘要 {} 字 + 最近 {} 条原文", oldPart.size(), summary.length(), recent.size());

        List<Message> compressed = new ArrayList<>();
        // 摘要作为一条 system 消息注入,让模型先看「之前聊了什么」
        compressed.add(new SystemMessage("以下是之前对话的摘要:\n" + summary));
        compressed.addAll(recent);
        return compressed;
    }

    private String summarize(List<Message> oldPart) {
        String text = oldPart.stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .map(m -> m.getMessageType() + ": " + m.getText())
                .collect(Collectors.joining("\n"));
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("把下面的对话历史总结成一段简洁摘要,保留关键事实、用户信息、重要结论。"),
                new UserMessage(text)));
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
