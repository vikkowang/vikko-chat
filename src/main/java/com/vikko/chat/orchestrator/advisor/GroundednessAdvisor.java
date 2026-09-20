package com.vikko.chat.orchestrator.advisor;

import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;

/**
 * 幻觉检测(浅版)advisor:检测「无出处的实质性回答」,追加提示。
 *
 * <p>原理:检索型回答(RAG)会以「📚 参考 / 来源 / 引用」结尾;而一段较长的实质性回答
 * 若没有任何引用标记,大概率是模型「没走检索、凭记忆作答」——这正是幻觉的温床。
 * 本 advisor 在 after 阶段检测到这种情况,就追加一句「⚠️ 未检索到依据,请核实」,
 * 提示但不拦截(浅版定位,不调 LLM、不依赖 RAG 返回原始证据块)。
 *
 * <p>启发式规则:
 * <ol>
 *   <li>回答太短(短于 {@link #MIN_CLAIM_LENGTH})视为「工具结果」(计算/查天气/查状态),跳过;</li>
 *   <li>回答含引用标记(📚/参考/来源/引用/[1])视为有出处,跳过;</li>
 *   <li>其余(较长且无引用)判定为「无依据回答」,追加警示。</li>
 * </ol>
 */
public class GroundednessAdvisor implements CallAdvisor {

    private static final String NAME = "groundedness";

    /** 回答长度阈值:短于此视为工具结果,不做 groundedness 检查。 */
    private static final int MIN_CLAIM_LENGTH = 50;

    /** 引用标记:出现任一即认为「有出处」。 */
    private static final List<String> CITATION_MARKERS = List.of("📚", "参考", "来源", "引用", "[1]");

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // 无 before 逻辑,直接调 LLM,再在 after 阶段检查
        ChatClientResponse resp = chain.nextCall(request);
        return after(resp);
    }

    /** after 阶段:检测无出处的实质性回答并追加警示。 */
    private ChatClientResponse after(ChatClientResponse response) {
        ChatResponse chatResponse = response.chatResponse();
        AssistantMessage output = chatResponse.getResult().getOutput();
        String text = output.getText();
        if (text == null || text.isBlank() || text.length() < MIN_CLAIM_LENGTH) {
            return response;  // 短回答 = 工具结果,不检查
        }
        boolean hasCitation = CITATION_MARKERS.stream().anyMatch(text::contains);
        if (hasCitation) {
            return response;  // 有出处,跳过
        }
        // 无出处的实质性回答 → 追加警示,重建响应(提示,不拦截)
        AssistantMessage flagged = new AssistantMessage(text + "\n\n⚠️ 未检索到依据,请核实");
        Generation flaggedGeneration = new Generation(flagged, chatResponse.getResult().getMetadata());
        ChatResponse flaggedResponse = ChatResponse.builder()
                .from(chatResponse)
                .generations(List.of(flaggedGeneration))
                .build();
        return ChatClientResponse.builder()
                .chatResponse(flaggedResponse)
                .context(response.context())
                .build();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        // 排在 SensitiveDataAdvisor(MAX-1)之前、SimpleLoggerAdvisor(0)之后:
        // after 阶段在 PII 脱敏之后、日志记录之前执行,检查最终答案。
        return Ordered.LOWEST_PRECEDENCE - 2;
    }
}
