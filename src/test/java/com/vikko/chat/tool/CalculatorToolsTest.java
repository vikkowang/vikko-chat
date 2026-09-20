package com.vikko.chat.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 计算工具的 golden 断言:确定性工具,固定输入 → 固定输出,零 LLM、零网络。
 */
class CalculatorToolsTest {

    private final CalculatorTools calculator = new CalculatorTools();

    @Test
    void add() {
        assertEquals(13.0, calculator.calculate(10, 3, "add"));
    }

    @Test
    void subtract() {
        assertEquals(7.0, calculator.calculate(10, 3, "subtract"));
    }

    @Test
    void multiply() {
        assertEquals(96.0, calculator.calculate(12, 8, "multiply"));
    }

    @Test
    void divide() {
        assertEquals(2.5, calculator.calculate(10, 4, "divide"));
    }

    @Test
    void divideByZeroThrows() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(1, 0, "divide"));
    }

    @Test
    void unknownOperationThrows() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(1, 2, "bogus"));
    }
}
