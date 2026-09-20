package com.vikko.chat.orchestrator.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vikko.chat.orchestrator.ConversationSummarizer;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

/**
 * 带摘要压缩的记忆 advisor,替代 {@code MessageChatMemoryAdvisor}。
 *
 * <p>与 Spring AI 自带的 {@code MessageChatMemoryAdvisor} 的区别:后者加载<b>全量</b>历史;
 * 这里在 before 阶段用 {@link ConversationSummarizer} 把超长历史压成「摘要 + 最近几条」再注入。
 * 摘要只在本次请求里生效,<b>不写回 {@link ChatMemory}</b>(非破坏,原始历史原样保留)。
 *
 * <p>before:取历史 → 压缩 → 拼 prompt(人设 system 最前、历史居中、用户消息最后)→ 落库当前用户消息;
 * after:落库 assistant 回复。读写接口与 {@code MessageChatMemoryAdvisor} 完全一致。
 */
public class SummarizingMemoryAdvisor implements CallAdvisor {

    private static final String NAME = "summarizing-memory";

    private final ChatMemory chatMemory;
    private final ConversationSummarizer summarizer;

    public SummarizingMemoryAdvisor(ChatMemory chatMemory, ConversationSummarizer summarizer) {
        this.chatMemory = chatMemory;
        this.summarizer = summarizer;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientRequest processed = before(request);
        ChatClientResponse response = chain.nextCall(processed);
        return after(response);
    }

    /** before 阶段:加载并压缩历史,注入 prompt,落库用户消息。 */
    private ChatClientRequest before(ChatClientRequest request) {
        String conversationId = getConversationId(request.context());

        // 1. 取历史(当前这条用户消息尚未落库,拿到的是上一轮及之前的历史)
        List<Message> memory = chatMemory.get(conversationId);

        // 2. 非破坏压缩:超阈值返回「摘要 + 最近几条」,否则原样返回(不写回 chatMemory)
        List<Message> history = summarizer.compress(memory);

        // 3. 拼 prompt:人设 system 最前 → 历史(含摘要)→ 其余(用户消息)
        List<Message> instructions = request.prompt().getInstructions();
        List<Message> processed = new ArrayList<>();
        for (Message m : instructions) {
            if (m instanceof SystemMessage) processed.add(m);
        }
        processed.addAll(history);
        for (Message m : instructions) {
            if (!(m instanceof SystemMessage)) processed.add(m);
        }

        // 4. 用处理后的消息重建请求
        ChatClientRequest processedRequest = request.mutate()
            .prompt(request.prompt().mutate().messages(processed).build())
            .build();

        // 5. 先落库当前用户消息(与 MessageChatMemoryAdvisor 一致:模型未响应就存用户消息)
        Message userMessage = processedRequest.prompt().getLastUserOrToolResponseMessage();
        chatMemory.add(conversationId, userMessage);

        return processedRequest;
    }

    /** after 阶段:落库 assistant 回复。 */
    private ChatClientResponse after(ChatClientResponse response) {
        List<Message> assistantMessages = new ArrayList<>();
        if (response.chatResponse() != null) {
            assistantMessages = response.chatResponse().getResults().stream()
                .map(g -> (Message) g.getOutput())
                .toList();
        }
        chatMemory.add(getConversationId(response.context()), assistantMessages);
        return response;
    }

    private String getConversationId(Map<String, Object> context) {
        return context.get(ChatMemory.CONVERSATION_ID).toString();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        // 与 MessageChatMemoryAdvisor 默认一致:尽早加载历史,排在蜜罐之后、日志/脱敏之前
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;
    }
}
