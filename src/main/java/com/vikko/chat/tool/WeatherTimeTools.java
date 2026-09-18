package com.vikko.chat.tool;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 天气与时间工具。被「天气时间 agent」和 MCP Server 共用。
 */
@Component
public class WeatherTimeTools {

    @Tool(description = "根据城市名返回模拟天气(温度与天气状况)")
    public String getWeather(String city) {
        // 模拟数据:用一个稳定的伪随机,保证同一城市结果一致
        int hash = Math.abs(city.hashCode());
        String[] conditions = {"晴", "多云", "小雨", "阴"};
        int temp = 10 + hash % 20;
        String condition = conditions[hash % conditions.length];
        return city + " 当前" + condition + "," + temp + "°C";
    }

    @Tool(description = "返回指定时区的当前时间,zone 用 IANA 时区名,例如 Asia/Shanghai")
    public String getCurrentTime(String zone) {
        try {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(zone));
            return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的时区: " + zone);
        }
    }
}
