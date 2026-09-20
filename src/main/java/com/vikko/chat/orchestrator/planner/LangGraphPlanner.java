package com.vikko.chat.orchestrator.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vikko.chat.agent.Agent;
import com.vikko.chat.orchestrator.AgentOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 用 LangGraph4j 的 {@link StateGraph} 表达 ReAct 循环的 planner——{@link AgentOrchestrator} 的「框架化」实现。
 *
 * <h2>为什么要这一版</h2>
 * {@link ReActPlanner} 手写 for 循环跑 ReAct,直观、每步可见;但循环逻辑「写死」在代码里,
 * 一旦编排变复杂(多路并行、失败重试、条件跳转、人机协作),就得自己堆 if/else,还很难持久化中间状态。
 * LangGraph4j 把编排抽象成<b>图</b>:<b>节点 = 一个动作</b>,<b>边 = 控制流</b>。同一个 ReAct 循环,
 * 用图表达就是「两节点 + 一条条件边」——这也是 LangGraph(及其 Java 移植)里最经典的 ReAct 图。
 *
 * <h2>图结构(与手写 ReAct 逐行对应)</h2>
 * <pre>
 *   START ──► [agent 节点] ──(还有工具调用吗?)──► [tools 节点] ──► 回到 agent
 *                │
 *                └────(没有工具调用 → 写入 answer → END,输出最终答案)
 * </pre>
 * <ul>
 *   <li>{@code agent} 节点 = <b>Reason</b>:把当前上下文发给模型,拿回「工具调用」或「最终答案」;</li>
 *   <li>{@code tools} 节点 = <b>Act + Observe</b>:执行工具、把结果拼回上下文;</li>
 *   <li>{@code agent} 之后的条件边 = <b>循环判断</b>:还有工具调用 → 走 tools;没有 → END。</li>
 * </ul>
 *
 * <h2>状态(schema)</h2>
 * 图共享状态是 {@link PlannerState},只有两个键:
 * <ul>
 *   <li>{@code messages}:用 {@code Channels.appenderWithDuplicate} 累加消息。每次节点执行时把
 *       新产生的消息<b>追加</b>进去。注意没用默认的 {@code Channels.appender}——它自带去重,会
 *       把相同内容的消息丢掉,而对话消息是<b>不能去重</b>的;</li>
 *   <li>{@code answer}:普通键(无 channel,直接覆盖)。{@code agent} 节点算出最终答案时写入,
 *       同时作为图的<b>结束信号</b>供条件边判断。</li>
 * </ul>
 *
 * <h2>和手写版共用什么</h2>
 * 完全复用同一套:{@code ChatModel} + {@code ToolCallingManager} + 5 个子 agent(经
 * {@link ToolCallbacks#from} 转成工具回调)+ {@link AgentOrchestrator#SYSTEM_PROMPT} 人设。
 * 因此两版<b>行为一致</b>,只是「编排壳」不同(手写 for 循环 vs 图)。
 *
 * <h2>三个实现细节</h2>
 * <ul>
 *   <li><b>手动执行工具</b>:{@code internalToolExecutionEnabled(false)} 让 {@code agent} 节点
 *       只拿到模型返回的「工具调用」、不自动执行,再由 {@code tools} 节点手动
 *       {@code executeToolCalls}——这样每一步都落在图上,可观测、可干预;</li>
 *   <li><b>重建 ChatResponse</b>:{@code tools} 节点要把上一步的 assistant 消息重新包成一个
 *       {@link ChatResponse}({@code new Generation(assistantMessage)}),因为
 *       {@link ToolCallingManager#executeToolCalls} 需要从 response 里读工具调用。</li>
 *   <li><b>禁用状态克隆</b>:Spring AI 的 {@code Message} 没实现 {@code Serializable},而 LangGraph4j
 *       默认用 Java 序列化克隆状态(每个节点后做快照)会抛 {@code NotSerializableException};故在
 *       {@code plan()} 里用 {@code disableCloneState()} 关闭克隆、绕开序列化。</li>
 * </ul>
 *
 * <h2>防死循环</h2>
 * 手写版用 {@code MAX_STEPS=10} 兜底;这里用 {@code recursionLimit(20)}——每个 ReAct 步 =
 * agent + tools 两个节点,20 ≈ 10 步。超过上限 LangGraph4j 会抛异常,由 {@link #plan} 捕获后
 * 返回「达到最大迭代次数」。
 *
 * <h2>下一步(本版未做)</h2>
 * LangGraph4j 相对手写版最大的增量是 <b>checkpoint 持久化</b>:在 {@code CompileConfig} 里配
 * {@code checkpointSaver},把每个节点执行后的状态落盘,支持失败恢复 / 回放 / 调试。留到后续。
 */
@Slf4j
@Component
public class LangGraphPlanner implements AgentOrchestrator {

    /** 状态里累加消息的键。 */
    private static final String MESSAGES = "messages";
    /** 状态里存放最终答案的键(也是图结束的信号)。 */
    private static final String ANSWER = "answer";
    /** 递归上限:记账含 START/END 等每次节点访问,20 约允许 8 个工具回合(手写版是 10 步)。 */
    private static final int RECURSION_LIMIT = 20;

    // 裸 ChatModel:call() 只返回模型原始响应(含工具调用、不自动执行)
    private final ChatModel chatModel;
    // 工具执行器:executeToolCalls() 负责「单步」执行工具
    private final ToolCallingManager toolCallingManager;
    // 所有子 agent 转成的工具回调(与 ReActPlanner 完全相同)
    private final ToolCallback[] toolCallbacks;
    // 编译后的图:节点/边在构造期定义好,运行期只调用 invoke
    private final CompiledGraph<PlannerState> graph;

    public LangGraphPlanner(ChatModel chatModel, ToolCallingManager toolCallingManager, List<Agent> agents) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.toolCallbacks = ToolCallbacks.from(agents.toArray());
        this.graph = buildGraph();
    }

    @Override
    public String plan(List<Message> history, String userMessage) {
        // 组装初始上下文(与手写版一致):系统人设 + 历史 + 用户消息,作为图的初始状态
        List<Message> initial = new ArrayList<>();
        initial.add(new SystemMessage(SYSTEM_PROMPT));
        initial.addAll(history);
        initial.add(new UserMessage(userMessage));
        try {
            // 跑图:从 START → agent 开始,按条件边在 agent/tools 之间循环,直到写 answer 走 END。
            // disableCloneState():关闭 LangGraph4j 每个节点后的状态克隆。克隆用 Java 序列化,
            // 而 Spring AI Message 不可序列化会抛 NotSerializableException;节点内已做防御性拷贝,禁用它安全。
            PlannerState result = graph.invoke(Map.of(MESSAGES, initial),
                            RunnableConfig.builder().disableCloneState().build())
                    .orElseThrow(() -> new IllegalStateException("LangGraph 执行未产出最终状态"));
            return result.answer().orElse("达到最大迭代次数,任务未完成。");
        } catch (IllegalStateException e) {
            // 仅递归上限这一种情况按「达到最大迭代次数」兜底;其余异常(模型/工具/图执行错误)原样抛出
            if (e.getMessage() != null && e.getMessage().startsWith("Maximum number of iterations")) {
                log.warn("LangGraph 达到递归上限,任务未完成", e);
                return "达到最大迭代次数,任务未完成。";
            }
            throw e;
        }
    }

    /**
     * 定义并编译 ReAct 图。
     *
     * <p>节点/边一览:
     * <ul>
     *   <li>{@code START → agent}:图入口直接进 agent 节点;</li>
     *   <li>{@code agent → tools}(条件边):还有工具调用才走 tools;</li>
     *   <li>{@code agent → END}(条件边):没有工具调用、答案已写出,结束;</li>
     *   <li>{@code tools → agent}(普通边):工具执行完回到 agent,进入下一轮 Reason。</li>
     * </ul>
     */
    private CompiledGraph<PlannerState> buildGraph() {
        try {
            // StateGraph 构造:第一参是状态 schema,第二参是「初始数据 → 状态对象」的工厂。
            // 序列化问题不在图构建这里处理,而是在 plan() 里用 disableCloneState() 关闭状态克隆。
            return new StateGraph<>(PlannerState.SCHEMA, PlannerState::new)
                    .addNode("agent", node_async(this::agent))
                    .addNode("tools", node_async(this::tools))
                    .addEdge(START, "agent")
                    .addEdge("tools", "agent")
                    // 条件边:agent 节点执行完后,按 routeAfterAgent 的返回值路由
                    // routeAfterAgent 返回 "tools" 或 "end",这里把它们映射到实际节点名 / END
                    .addConditionalEdges("agent", edge_async(this::routeAfterAgent),
                            Map.of("tools", "tools", "end", END))
                    .compile(CompileConfig.builder().recursionLimit(RECURSION_LIMIT).build());
        } catch (GraphStateException e) {
            // 图结构定义错误(如节点/边引用不存在)在编译期暴露,直接启动失败
            throw new IllegalStateException("构建 LangGraph 图失败", e);
        }
    }

    /**
     * {@code agent} 节点 = Reason:问模型。
     *
     * <p>读当前消息列表,调用模型(带工具回调、但<b>不自动执行</b>)。结果两种:
     * <ul>
     *   <li>模型直接给出答案(无工具调用)→ 写入 {@code answer} 键,结束图;</li>
     *   <li>模型返回工具调用 → 把这条 assistant 消息追加进 {@code messages},交给 tools 节点。</li>
     * </ul>
     */
    private Map<String, Object> agent(PlannerState state) {
        List<Message> messages = new ArrayList<>(state.messages());
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false)
                .build();
        ChatResponse response = chatModel.call(new Prompt(messages, options));
        AssistantMessage assistantMessage = response.getResult().getOutput();

        if (!assistantMessage.hasToolCalls()) {
            log.info("LangGraph agent 节点:模型直接给出最终回答,结束");
            return Map.of(ANSWER, assistantMessage.getText());
        }

        List<String> names = assistantMessage.getToolCalls().stream()
                .map(AssistantMessage.ToolCall::name)
                .toList();
        log.info("LangGraph agent 节点 Action:调用 {}", names);
        return Map.of(MESSAGES, List.of(assistantMessage));
    }

    /**
     * {@code tools} 节点 = Act + Observe:执行工具、拼回结果。
     *
     * <p>此时 {@code messages} 最后一条是 agent 节点刚产出的 assistant 消息(含工具调用)。
     * 需要重建一个 {@link ChatResponse} 把它包进去,才能调 {@code executeToolCalls}(该方法
     * 从 response 里读工具调用)。执行后把工具结果消息追加回 {@code messages},条件边会把
     * 控制流交回 agent 节点进入下一轮。
     */
    private Map<String, Object> tools(PlannerState state) {
        List<Message> messages = new ArrayList<>(state.messages());
        AssistantMessage assistantMessage = (AssistantMessage) messages.get(messages.size() - 1);

        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false)
                .build();
        Prompt prompt = new Prompt(messages, options);
        ChatResponse response = ChatResponse.builder()
                .generations(List.of(new Generation(assistantMessage)))
                .build();

        ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
        // 注意:conversationHistory() 返回的是「完整历史 + 本次 assistant + 工具结果」,而 agent 节点
        // 已经把这个 assistant 追加进 messages 了。若把整段历史再追加一遍,会出现 assistant 重复、
        // 工具结果没紧跟 assistant 的情况,DeepSeek 会报 400。这里只取最后一条(工具结果)追加。
        List<Message> observation = result.conversationHistory();
        Message toolResponse = observation.get(observation.size() - 1);
        log.info("LangGraph tools 节点 Observation:{}", toolResponse);
        return Map.of(MESSAGES, List.of(toolResponse));
    }

    /**
     * 条件边(agent 节点之后):决定下一步走哪。
     *
     * @return {@code "end"} 表示答案已写出、结束图;{@code "tools"} 表示还要继续执行工具。
     */
    private String routeAfterAgent(PlannerState state) {
        return state.answer().isPresent() ? "end" : "tools";
    }

    /**
     * 图的共享状态。
     *
     * <p>{@code messages} 用 appender channel 累加(去重会丢消息,故用 appenderWithDuplicate);
     * {@code answer} 没配 channel,是普通键,后写覆盖前写。
     */
    static class PlannerState extends AgentState {
        /** 状态 schema:只声明需要 reducer 的键(messages);answer 是普通键,无需 channel。 */
        static final Map<String, Channel<?>> SCHEMA = Map.of(
                MESSAGES, Channels.<Message>appenderWithDuplicate(ArrayList::new)
        );

        PlannerState(Map<String, Object> initData) {
            super(initData);
        }

        /** 取当前累计的对话消息(Spring AI {@link Message} 列表)。 */
        List<Message> messages() {
            return this.<List<Message>>value(MESSAGES).orElse(List.of());
        }

        /** 取最终答案(只有 agent 节点走 END 分支前才会写入)。 */
        Optional<String> answer() {
            return value(ANSWER);
        }
    }
}
