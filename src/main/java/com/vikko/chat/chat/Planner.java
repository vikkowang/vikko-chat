package com.vikko.chat.chat;

import java.util.ArrayList;
import java.util.List;

import com.vikko.chat.agent.Agent;
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
 * 显式 ReAct 循环的 planner(总调度)。
 *
 * <p><b>ReAct</b> = Reason(思考)+ Act(行动)+ Observe(观察) 的循环:
 * <ol>
 *   <li><b>Reason(思考)</b>:把当前上下文发给模型,让它决定「下一步做什么」。
 *       在 Spring AI 里,这个决定体现为模型在响应里返回一个<b>工具调用</b>
 *       ({@code AssistantMessage.hasToolCalls() == true}),而不是直接给出最终答案。</li>
 *   <li><b>Act(行动)</b>:执行这个工具调用——本项目里就是调用某个子 agent
 *       (检索 / 钉钉 / 计算 / 天气时间 / 用户管理)。</li>
 *   <li><b>Observe(观察)</b>:把执行结果({@code ToolResponseMessage})拼回对话历史,
 *       让模型看到结果,进入下一轮思考。</li>
 *   <li>循环,直到模型不再返回工具调用——那一刻的输出就是最终回答。</li>
 * </ol>
 *
 * <p><b>为什么要手写这个循环</b>:{@code ChatClient} 默认在内部自动跑完整个
 * 「模型 → 调工具 → 拼结果 → 再问模型」的循环,是个黑盒,你看不到中间每一步。
 * 这里手写循环,把每一步的 Action / Observation 都打到日志里,让「总调度是怎么
 * 一步步委派子 agent 的」显式可见——这正是学 multi-agent 编排的核心。
 *
 * <p><b>两个关键 API 选择</b>:
 * <ul>
 *   <li>用<b>裸</b> {@link ChatModel#call(Prompt)} 拿原始响应(含工具调用、但<b>不自动执行</b>),
 *       而不是 {@code ChatClient}(它会自动跑完整循环,你就拿不到中间步骤了);</li>
 *   <li>{@link ToolCallingChatOptions} 的 internalToolExecutionEnabled 设为 {@code false},
 *       确保模型只返回工具调用、不自动执行,再由本循环用
 *       {@link ToolCallingManager#executeToolCalls} 手动单步执行。</li>
 * </ul>
 */
@Slf4j
@Component
public class Planner {

    /** 最大迭代次数,防止模型一直调工具死循环。 */
    private static final int MAX_STEPS = 10;

    /** 规划者的人设:明确它要做「拆解 → 委派 → 汇总」。 */
    private static final String SYSTEM_PROMPT = """
            你是总调度(planner)。收到用户任务后:
            1. 分析任务,判断需要委派给哪些子 agent(检索/钉钉/计算/天气时间/用户管理);
            2. 逐个调用需要的子 agent;
            3. 汇总各 agent 的结果,给出最终回答。
            """;

    // 裸 ChatModel:call() 只返回模型原始响应(含工具调用、不自动执行)
    private final ChatModel chatModel;
    // 工具执行器:executeToolCalls() 负责「单步」执行工具
    private final ToolCallingManager toolCallingManager;
    // 所有子 agent 转成的工具回调(通过 Agent 接口 List 注入,见构造器)
    private final ToolCallback[] toolCallbacks;

    public Planner(ChatModel chatModel, ToolCallingManager toolCallingManager, List<Agent> agents) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        // 把实现 Agent 接口的所有子 agent 统一扫成 ToolCallback(靠各实现类上的 @Tool 注解)
        this.toolCallbacks = ToolCallbacks.from(agents.toArray());
    }

    /** 单轮(无历史)入口。 */
    public String plan(String userMessage) {
        return plan(List.of(), userMessage);
    }

    /**
     * 手写 ReAct 循环。
     *
     * @param history     之前的对话历史(多轮记忆用;单轮传空列表)
     * @param userMessage 用户当前消息
     * @return 模型最终回答
     */
    public String plan(List<Message> history, String userMessage) {
        // 组装初始上下文:系统人设 + 历史 + 用户消息
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.addAll(history);
        messages.add(new UserMessage(userMessage));

        // ReAct 循环:每轮 = Reason(问模型)→ Act(执行工具)→ Observe(拼回结果)
        for (int step = 0; step < MAX_STEPS; step++) {
            // 构建带工具的 options;internalToolExecutionEnabled(false) 让模型只返回工具调用、不自动执行
            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .toolCallbacks(toolCallbacks)
                    .internalToolExecutionEnabled(false)
                    .build();
            Prompt prompt = new Prompt(messages, options);

            // Reason:问模型。裸 ChatModel.call 拿到原始响应(含工具调用,不自动执行)
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

            // Observe:把结果拼回对话历史,让模型在下一轮「看到」这次行动的结果
            messages.addAll(observation);
        }
        log.warn("达到最大迭代次数 {},任务未完成", MAX_STEPS);
        return "达到最大迭代次数,任务未完成。";
    }
}
