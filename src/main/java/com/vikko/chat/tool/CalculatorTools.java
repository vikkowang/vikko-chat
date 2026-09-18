package com.vikko.chat.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 计算工具:四则运算。被「计算 agent」和 MCP Server 共用。
 */
@Component
public class CalculatorTools {

    @Tool(description = "对两个数做四则运算。operation 取值:add(加)、subtract(减)、multiply(乘)、divide(除)")
    public double calculate(double a, double b, String operation) {
        return switch (operation) {
            case "add" -> a + b;
            case "subtract" -> a - b;
            case "multiply" -> a * b;
            case "divide" -> {
                if (b == 0) {
                    throw new IllegalArgumentException("除数不能为 0");
                }
                yield a / b;
            }
            default -> throw new IllegalArgumentException("不支持的运算: " + operation);
        };
    }
}
