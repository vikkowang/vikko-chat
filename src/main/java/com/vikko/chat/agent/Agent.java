package com.vikko.chat.agent;

/**
 * Agent 标记接口。
 *
 * <p>所有子 agent 实现它,以便用 {@code List<Agent>} 统一注入到 Planner——加新 agent 只需新建类、无需改 Planner。
 * 工具发现仍靠各实现类上的 {@code @Tool} 注解。
 */
public interface Agent {
}
