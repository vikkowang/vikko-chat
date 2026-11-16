package com.vikko.chat.orchestrator;

import java.util.Arrays;
import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

/**
 * 总调度(planner)的编排抽象。
 *
 * <p>同一套「拆解 → 委派子 agent → 汇总」的逻辑,有两种实现:
 * <ul>
 *   <li>{@link ReActPlanner}:手写 ReAct 循环,逐步可见;</li>
 *   <li>{@link LangGraphPlanner}:用 LangGraph4j 的 StateGraph 表达同一个循环。</li>
 * </ul>
 * 两种实现<b>都注册为 Bean</b>,由 {@link PlannerFactory} 按 {@code app.planner.mode}
 * (react / langgraph)选出实际使用的一个,见 application.yml。
 */
public interface AgentOrchestrator {

    /** 规划者人设:明确它要做「拆解 → 委派 → 汇总」。两种实现共用。 */
    String SYSTEM_PROMPT = """
            你是总调度(planner)。收到用户任务后:
            1. 分析任务,判断需要委派给哪些子 agent(以「本次可用工具」为准);
            2. 逐个调用需要的子 agent;
            3. 汇总各 agent 的结果,给出最终回答;
            4. 对多步骤的复杂任务,每完成一步用 saveTaskProgress 记录进度(长任务在上下文重置后可恢复),
               必要时先用 loadTaskProgress 读取已有进度。
            """;

    /**
     * 按检索结果拼出 system prompt:基础人设 + 「本次可用工具」清单,避免模型调用未被挂载的工具。
     *
     * @param selectedTools 本次请求检索出的工具子集
     */
    static String systemPrompt(ToolCallback[] selectedTools) {
        String names = Arrays.stream(selectedTools)
                .map(cb -> cb.getToolDefinition().name())
                .collect(java.util.stream.Collectors.joining("、"));
        return SYSTEM_PROMPT + "\n本次请求可用工具(只能调用这些):" + names;
    }

    /**
     * 执行一次调度。
     *
     * @param history     之前的对话历史(多轮记忆用;单轮传空列表)
     * @param userMessage 用户当前消息
     * @return 模型最终回答
     */
    String plan(List<Message> history, String userMessage);

    /** 单轮(无历史)入口。 */
    default String plan(String userMessage) {
        return plan(List.of(), userMessage);
    }
}
