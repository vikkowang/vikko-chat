package com.vikko.chat.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 天气/时间工具的 golden 断言:天气是稳定伪随机(同城市结果一致),时间只断言格式与时区合法性。
 */
class WeatherTimeToolsTest {

    private final WeatherTimeTools tools = new WeatherTimeTools();

    @Test
    void getWeatherIsDeterministic() {
        // 同一城市两次结果一致(伪随机稳定),且格式为「城市 当前X, N°C」
        String a = tools.getWeather("北京");
        String b = tools.getWeather("北京");
        assertEquals(a, b);
        assertTrue(a.startsWith("北京 当前"), "实际: " + a);
        assertTrue(a.endsWith("°C"), "实际: " + a);
    }

    @Test
    void getCurrentTimeFormat() {
        // 时区合法 → 返回 yyyy-MM-dd HH:mm:ss 开头的字符串(时区缩写因系统而异,只断言格式)
        String now = tools.getCurrentTime("Asia/Shanghai");
        assertTrue(now.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} .+"), "实际: " + now);
    }

    @Test
    void getCurrentTimeInvalidZoneThrows() {
        assertThrows(IllegalArgumentException.class, () -> tools.getCurrentTime("Invalid/Zone"));
    }
}
