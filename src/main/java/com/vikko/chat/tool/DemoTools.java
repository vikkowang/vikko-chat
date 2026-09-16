package com.vikko.chat.tool;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.vikko.chat.mapper.UserStatusMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 演示工具集。
 *
 * <p>这里的 {@code @Tool} 方法有两个消费方:
 * <ul>
 *   <li>函数调用(function calling):{@code ChatClient.tools(demoTools)} 直接调用;</li>
 *   <li>MCP:{@link com.vikko.chat.config.ToolConfig} 把它包装成 {@code ToolCallbackProvider},
 *       被 MCP Server 经 MCP 协议暴露。</li>
 * </ul>
 * 同一份工具实现、两种暴露方式——这正是理解“函数调用 vs MCP”的核心。
 */
@Component
public class DemoTools {

    private final UserStatusMapper userStatusMapper;

    public DemoTools(UserStatusMapper userStatusMapper) {
        this.userStatusMapper = userStatusMapper;
    }

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

    @Tool(description = "查询用户状态。传入用户名,返回其账户状态(正常/停用/封禁)")
    public String queryUserStatus(String username) {
        String statusValue = userStatusMapper.findStatusByUsername(username);
        if (statusValue == null) {
            return "未找到用户: " + username;
        }
        UserStatus status = UserStatus.valueOf(statusValue);
        return username + " 的状态: " + status.getDescription();
    }

    @Tool(description = "修改用户状态。username 是用户名,status 取值:ACTIVE(正常)、INACTIVE(停用)、BANNED(封禁)")
    public String updateUserStatus(String username, String status) {
        UserStatus target;
        try {
            target = UserStatus.valueOf(status.trim().toUpperCase());
        } catch (NullPointerException | IllegalArgumentException e) {
            return "不支持的状态: " + status + ",合法取值:ACTIVE(正常)/INACTIVE(停用)/BANNED(封禁)";
        }
        int updated = userStatusMapper.updateStatusByUsername(username, target.name());
        if (updated == 0) {
            return "未找到用户: " + username;
        }
        return username + " 的状态已更新为: " + target.getDescription();
    }
}
