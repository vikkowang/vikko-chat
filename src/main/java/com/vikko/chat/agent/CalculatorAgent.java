package com.vikko.chat.agent;

import com.vikko.chat.tool.CalculatorTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 计算 agent:一个「数学助手」,内部挂 calculate 工具。
 * 复合算式(如 12*8+5)会让它分步调用工具,体现多步能力。
 */
@Component
public class CalculatorAgent implements Agent {

    private final ChatClient client;
    private final CalculatorTools calculatorTools;

    public CalculatorAgent(ChatClient.Builder builder, CalculatorTools calculatorTools) {
        this.client = builder.build();
        this.calculatorTools = calculatorTools;
    }

    @Tool(description = "做数学计算(支持复合算式,如 '12 乘 8 再加 5')。返回结果和简要计算过程。")
    public String calculate(String expression) {
        return client.prompt()
                .system("""
                        你是一名数学计算助手。
                        要求:
                        1. 把用户用自然语言描述的算式准确拆解成四则运算;
                        2. 复合算式(如 12*8+5)可以分步调用计算工具,逐步求值;
                        3. 给出最终结果,并附简短的计算过程。
                        """)
                .user(expression)
                .tools(calculatorTools)
                .call()
                .content();
    }
}
