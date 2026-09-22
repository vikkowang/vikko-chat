package com.vikko.chat.orchestrator.advisor;

import java.util.ArrayList;
import java.util.List;

import com.vikko.chat.mapper.TaskStateMapper;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

/**
 * 状态栏(Status Bar)advisor:每轮把「任务进度」提炼成显式状态,以极低 token 成本注入上下文。
 *
 * <p>理论基础(见《深入理解 AI Agent》2.6):模型擅长从上下文检索、不擅长主动归纳,「进行到哪一步」
 * 这类隐式状态分散在历史里,模型每次要现算、易错。状态栏把这些隐式状态显式化,让它「瞥一眼」就知道进度。
 *
 * <p>本实现只注入 {@code task_state} 表里已保存的进度笔记(由 {@code saveTaskProgress} 写入)。
 * 若没有进度则不注入,零开销。
 */
public class StatusBarAdvisor implements CallAdvisor {

    private static final String NAME = "status-bar";

    private final TaskStateMapper taskStateMapper;

    public StatusBarAdvisor(TaskStateMapper taskStateMapper) {
        this.taskStateMapper = taskStateMapper;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientRequest injected = before(request);
        return chain.nextCall(injected);
    }

    private ChatClientRequest before(ChatClientRequest request) {
        String conversationId = request.context().get(ChatMemory.CONVERSATION_ID).toString();
        String progress = taskStateMapper.findProgressByConversationId(conversationId);
        if (progress == null || progress.isBlank()) {
            return request;
        }
        Prompt prompt = request.prompt();
        List<Message> messages = new ArrayList<>(prompt.getInstructions());
        messages.add(new SystemMessage("【任务进度】" + progress));
        Prompt newPrompt = new Prompt(messages, prompt.getOptions());
        return ChatClientRequest.builder().prompt(newPrompt).context(request.context()).build();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        // 排在记忆(约 200)之后、日志之前:让进度状态在历史加载后注入,靠近末尾(不破坏稳定前缀)
        return Ordered.LOWEST_PRECEDENCE - 3;
    }
}
