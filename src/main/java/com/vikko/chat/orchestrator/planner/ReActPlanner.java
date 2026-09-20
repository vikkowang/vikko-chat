package com.vikko.chat.orchestrator.planner;

import java.util.ArrayList;
import java.util.List;

import com.vikko.chat.agent.Agent;
import com.vikko.chat.orchestrator.AgentOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 手写 ReAct 循环的 planner(总调度)——{@link AgentOrchestrator} 的「手写循环」实现。
 *
 * <p><b>ReAct</b> = Reason(思考)+ Act(行动)+ Observe(观察) 的循环:
 * <ol>
 *   <li><b>Reason</b>:把当前上下文发给模型,让它返回一个<b>工具调用</b>(而非直接作答);</li>
 *   <li><b>Act</b>:执行这个工具调用——本项目里就是调用某个子 agent;</li>
 *   <li><b>Observe</b>:把结果({@code ToolResponseMessage})拼回对话历史,进入下一轮;</li>
 *   <li>循环,直到模型不再返回工具调用——那一刻的输出就是最终回答。</li>
 * </ol>
 *
 * <p>手写循环的用意是把每一步 Action / Observation 都打到日志里,让「总调度怎么一步步
 * 委派子 agent」显式可见。框架化版本见 {@link LangGraphPlanner}。
 */
@Slf4j
@Component
public class ReActPlanner implements AgentOrchestrator {

    /** 最大迭代次数,防止模型一直调工具死循环。 */
    private static final int MAX_STEPS = 10;

    // 裸 ChatModel:call() 只返回模型原始响应(含工具调用、不自动执行)
    private final ChatModel chatModel;
    // 工具执行器:executeToolCalls() 负责「单步」执行工具
    private final ToolCallingManager toolCallingManager;
    // 所有子 agent 转成的工具回调(通过 Agent 接口 List 注入)
    private final ToolCallback[] toolCallbacks;

    public ReActPlanner(ChatModel chatModel, ToolCallingManager toolCallingManager, List<Agent> agents) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.toolCallbacks = ToolCallbacks.from(agents.toArray());
    }

    @Override
    public String plan(List<Message> history, String userMessage) {
        // 组装初始上下文:系统人设 + 历史 + 用户消息
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.addAll(history);
        messages.add(new UserMessage(userMessage));

        // ReAct 循环:每轮 = Reason(问模型)→ Act(执行工具)→ Observe(拼回结果)
        for (int step = 0; step < MAX_STEPS; step++) {
            // internalToolExecutionEnabled(false):模型只返回工具调用、不自动执行
            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .toolCallbacks(toolCallbacks)
                    .internalToolExecutionEnabled(false)
                    .build();
            Prompt prompt = new Prompt(messages, options);

            // Reason:裸 ChatModel.call 拿到原始响应(含工具调用,不自动执行)
            ChatResponse response = chatModel.call(prompt);
            AssistantMessage assistantMessage = response.getResult().getOutput();

            // 模型没要调工具 → 这就是最终回答,结束循环
            if (!assistantMessage.hasToolCalls()) {
                log.info("ReAct 完成:共 {} 步", step + 1);
                return assistantMessage.getText();
            }

            // Act 前:记下这一轮「要调哪个 agent」
            List<String> names = assistantMessage.getToolCalls().stream()
                    .map(AssistantMessage.ToolCall::name)
                    .toList();
            log.info("ReAct 第 {} 步 Action:调用 {}", step + 1, names);

            // Act + Observe:手动执行工具,拿到结果消息(即 ToolResponseMessage)
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
            List<Message> observation = result.conversationHistory();
            log.info("ReAct 第 {} 步 Observation:{}", step + 1, observation);

            // Observe:conversationHistory() 返回的是「完整历史 + assistant + 工具结果」,
            // 直接用它替换 messages 即可;若 addAll 会把整段历史重复累积。
            messages.clear();
            messages.addAll(observation);
        }
        log.warn("达到最大迭代次数 {},任务未完成", MAX_STEPS);
        return "达到最大迭代次数,任务未完成。";
    }
}
