package com.vikko.chat.agent;

/**
 * Agent 标记接口。
 *
 * <p>所有子 agent 实现它,以便用 {@code List<Agent>} 统一注入到编排器(AgentOrchestrator 的实现)——加新 agent 只需新建类、无需改编排器。
 * 工具发现仍靠各实现类上的 {@code @Tool} 注解。
 */
public interface Agent {

    /** 子 agent 回传结果的最大长度:超出截断,避免大段工具结果污染 planner 的上下文(隔离优于压缩)。 */
    int MAX_RESULT_LENGTH = 500;

    /** 截断过长的返回结果——子 agent 只回传浓缩结论,呼应「隔离优于压缩」。 */
    default String cap(String result) {
        if (result == null || result.length() <= MAX_RESULT_LENGTH) {
            return result;
        }
        return result.substring(0, MAX_RESULT_LENGTH) + "\n...(内容已截断)";
    }
}
