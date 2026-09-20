package com.vikko.chat.orchestrator;

import java.util.Map;

import com.vikko.chat.orchestrator.planner.AdvisorPlanner;
import com.vikko.chat.orchestrator.planner.LangGraphPlanner;
import com.vikko.chat.orchestrator.planner.ReActPlanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 编排器工厂:按 {@code app.planner.mode} 选出实际使用的 {@link AgentOrchestrator}。
 *
 * <p>两种实现({@link ReActPlanner} / {@link LangGraphPlanner})<b>都注册为 Bean</b>,
 * 由本工厂根据配置二选一,再交给 {@code ChatService}。
 *
 * <p>选型配置在 application.yml 的 {@code app.planner.mode}(环境变量 {@code PLANNER_MODE}):
 * <ul>
 *   <li>{@code react} → {@link ReActPlanner}(手写循环);</li>
 *   <li>{@code langgraph} → {@link LangGraphPlanner}(LangGraph4j 图)。</li>
 * </ul>
 * 配置值非法时在<b>启动期</b>抛异常 fail-fast,而不是等到第一个请求才报 500。
 */
@Slf4j
@Component
public class PlannerFactory {

    /** 当前编排类型(react / langgraph),启动时由 {@code @Value} 解析注入。 */
    private final String type;
    /** 类型 → 编排器 的注册表,即「工厂」的产物目录。 */
    private final Map<String, AgentOrchestrator> planners;

    public PlannerFactory(ReActPlanner reactPlanner, LangGraphPlanner langGraphPlanner,
            AdvisorPlanner advisorPlanner, @Value("${app.planner.mode}") String type) {
        this.type = type;
        this.planners = Map.of(
                "react", reactPlanner,
                "langgraph", langGraphPlanner,
                "advisor", advisorPlanner);
        if (!planners.containsKey(type)) {
            throw new IllegalStateException("未知的 planner 类型: " + type + "(可选 react / langgraph / advisor)");
        }
        log.info("Planner 模式已选择: {}", type);
    }

    /** 按 {@code app.planner.mode} 返回对应的编排器。 */
    public AgentOrchestrator get() {
        return planners.get(type);
    }
}
