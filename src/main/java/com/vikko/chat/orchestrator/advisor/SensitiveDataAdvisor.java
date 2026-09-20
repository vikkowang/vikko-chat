package com.vikko.chat.orchestrator.advisor;

import java.util.List;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;

/**
 * 内容脱敏 advisor(After 阶段):对模型输出做正则打码,脱敏手机号 / 邮箱 / 身份证 / 长数字串。
 *
 * <p>实现 {@link CallAdvisor}:{@link #adviseCall} 先调用 {@code chain.nextCall(request)} 拿到
 * 模型原始输出,再把文本里的敏感信息替换成 {@code ***} 后重建响应返回。
 *
 * <p>顺序:设成 {@link Ordered#LOWEST_PRECEDENCE} - 1,排在链尾、仅次终结者
 * {@code ChatModelCallAdvisor}(同为 LOWEST_PRECEDENCE)之前——after 阶段最先执行,
 * 在 {@code SimpleLoggerAdvisor} 记录、{@code SummarizingMemoryAdvisor} 保存之前就先把 PII 打码,
 * 避免原始敏感信息进日志 / 进记忆。
 */
@Slf4j
public class SensitiveDataAdvisor implements CallAdvisor {

    private static final String NAME = "sensitive-data-masking";

    /** 注意顺序:先长类型(身份证)再短类型,避免长数字串把身份证的 17 位提前吃掉。 */
    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("唐杰"),                                             // 敏感词(姓名,测试用)
            Pattern.compile("1[3-9]\\d{9}"),                                    // 手机号(11 位)
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), // 邮箱
            Pattern.compile("\\b\\d{17}[\\dXx]\\b"),                             // 身份证(18 位)
            Pattern.compile("\\b\\d{16,19}\\b"));                                // 银行卡/长数字串

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        log.info(">>> SensitiveDataAdvisor 进入 adviseCall");
        // 脱敏只处理输出,无 before 逻辑
        ChatClientResponse resp = chain.nextCall(request); // ② 调用 LLM
        return after(resp);                                // ③ after:正则打码
    }

    /** after 阶段:对模型输出做正则打码,脱敏 PII。 */
    private ChatClientResponse after(ChatClientResponse response) {
        ChatResponse chatResponse = response.chatResponse();
        AssistantMessage output = chatResponse.getResult().getOutput();
        String text = output.getText();
        if (text == null || text.isBlank()) {
            return response;
        }
        String masked = text;
        for (Pattern pattern : PATTERNS) {
            masked = pattern.matcher(masked).replaceAll("***");
        }
        if (masked.equals(text)) {
            return response;
        }
        log.info(">>> SensitiveDataAdvisor 命中脱敏: {}", masked);
        AssistantMessage maskedMessage = new AssistantMessage(masked);
        Generation maskedGeneration = new Generation(maskedMessage, chatResponse.getResult().getMetadata());
        ChatResponse maskedResponse = ChatResponse.builder()
                .from(chatResponse)
                .generations(List.of(maskedGeneration))
                .build();
        return ChatClientResponse.builder()
                .chatResponse(maskedResponse)
                .context(response.context())
                .build();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        // 终结者 ChatModelCallAdvisor 的 order 也是 LOWEST_PRECEDENCE(MAX_VALUE)。
        // 若这里同样返回 MAX_VALUE,链构建时(pushAll 先 addFirst 再稳定排序)会把终结者排到本 advisor 之前,
        // 导致本 advisor 永远不被执行。故减 1,保证排在终结者之前、其余 advisor 之后。
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
