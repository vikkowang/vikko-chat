package com.vikko.chat.agent;

import com.vikko.chat.tool.WeatherTimeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 天气/时间 agent:一个「生活助手」,内部挂 getWeather + getCurrentTime 工具。
 * 查到天气/时间后会给生活建议,比直接调工具多一层「服务」。
 */
@Component
public class WeatherTimeAgent implements Agent {

    private final ChatClient client;
    private final WeatherTimeTools weatherTimeTools;

    public WeatherTimeAgent(ChatClient.Builder builder, WeatherTimeTools weatherTimeTools) {
        this.client = builder.build();
        this.weatherTimeTools = weatherTimeTools;
    }

    @Tool(description = "查询某个城市的天气,或某个时区的当前时间,并结合结果给出简短生活建议。")
    public String queryWeatherOrTime(String question) {
        return client.prompt()
                .system("""
                        你是生活助手,负责查询天气和时区时间。
                        要求:
                        1. 先调用工具拿到准确的天气/时间数据;
                        2. 结合结果给出简短实用的建议(如穿衣、出行提示);
                        3. 天气/时间数据要如实引用,不要编造。
                        """)
                .user(question)
                .tools(weatherTimeTools)
                .call()
                .content();
    }
}
