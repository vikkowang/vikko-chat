package com.vikko.chat.orchestrator.planner;

import java.util.List;

import com.vikko.chat.agent.Agent;
import com.vikko.chat.orchestrator.AgentOrchestrator;
import com.vikko.chat.orchestrator.ConversationSummarizer;
import com.vikko.chat.orchestrator.advisor.GroundednessAdvisor;
import com.vikko.chat.orchestrator.advisor.HoneypotAdvisor;
import com.vikko.chat.orchestrator.advisor.SensitiveDataAdvisor;
import com.vikko.chat.orchestrator.advisor.SummarizingMemoryAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 用 ChatClient + advisor 链跑「隐式工具循环」的 planner——{@link AgentOrchestrator} 的第三种实现。
 *
 * <p>与另外两种实现({@link ReActPlanner} 显式 ReAct、{@link LangGraphPlanner} 图)不同,这里用
 * {@link ChatClient} 的默认能力:模型返回工具调用后 ChatClient 自动执行、自动拼结果、再问模型,
 * 直到得到最终答案(黑盒循环)。代价是看不到中间每一步;好处是能挂 Spring AI 的 <b>advisor 链</b>。
 *
 * <p>挂的 advisor(按链顺序):
 * <ol>
 *   <li>{@link HoneypotAdvisor}:蜜罐对抗,提示注入检测;</li>
 *   <li>{@link SafeGuardAdvisor}:输入敏感词拦截(命中即拒答);</li>
 *   <li>{@link SummarizingMemoryAdvisor}:自动加载 / 保存对话历史,并对超长历史做摘要压缩;</li>
 *   <li>{@link SimpleLoggerAdvisor}:打印请求 / 响应日志;</li>
 *   <li>{@link GroundednessAdvisor}:幻觉检测(浅版,无出处回答追加警示);</li>
 *   <li>{@link SensitiveDataAdvisor}:输出内容脱敏(正则打码 PII)。</li>
 * </ol>
 */
@Component
public class AdvisorPlanner implements AgentOrchestrator {

    /** 单轮(无 conversationId)时使用的默认会话 id。 */
    private static final String DEFAULT_CONVERSATION_ID = "default";

    private final ChatClient client;

    public AdvisorPlanner(ChatClient.Builder builder, List<Agent> agents, ChatMemory chatMemory,
            ConversationSummarizer summarizer,
            @Value("${app.guard.sensitive-words:}") List<String> sensitiveWords) {
        ToolCallback[] toolCallbacks = ToolCallbacks.from(agents.toArray());
        this.client = builder
                .defaultToolCallbacks(toolCallbacks)
                .defaultAdvisors(
                        new HoneypotAdvisor(),
                        new SafeGuardAdvisor(sensitiveWords),
                        new SummarizingMemoryAdvisor(chatMemory, summarizer),
                        new SimpleLoggerAdvisor(),
                        new GroundednessAdvisor(),
                        new SensitiveDataAdvisor())
                .build();
    }

    @Override
    public String plan(List<Message> history, String userMessage) {
        // advisor 模式:历史由 SummarizingMemoryAdvisor 从 ChatMemory 自动加载(含摘要压缩),history 参数不再使用
        return planWithMemory(DEFAULT_CONVERSATION_ID, userMessage);
    }

    /** 走 advisor 链的多轮入口:记忆由 {@link SummarizingMemoryAdvisor} 自动读写(含压缩)。 */
    public String planWithMemory(String conversationId, String userMessage) {
        return client.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
