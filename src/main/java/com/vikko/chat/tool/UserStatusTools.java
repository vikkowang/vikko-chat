package com.vikko.chat.tool;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.vikko.chat.mapper.UserStatusMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 用户状态工具:查/改 user_status 表。被「用户管理 agent」和 MCP Server 共用。
 */
@Component
public class UserStatusTools {

    private final UserStatusMapper userStatusMapper;
    // 幂等:记录最近处理过的幂等键,相同键的重复调用直接跳过(内存态,演示用)
    private final Set<String> appliedIdempotencyKeys = ConcurrentHashMap.newKeySet();

    public UserStatusTools(UserStatusMapper userStatusMapper) {
        this.userStatusMapper = userStatusMapper;
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

    @Tool(description = "修改用户状态。username 是用户名,status 取值:ACTIVE(正常)、INACTIVE(停用)、BANNED(封禁)。idempotencyKey 可选:相同 key 的重复调用会被幂等跳过,不重复执行")
    public String updateUserStatus(String username, String status, String idempotencyKey) {
        // 幂等校验:相同幂等键的重复调用直接跳过,避免滑动窗口历史导致 Agent 反复执行同一操作
        if (idempotencyKey != null && !idempotencyKey.isBlank()
                && !appliedIdempotencyKeys.add(idempotencyKey)) {
            return "重复请求(幂等键已处理),已跳过:" + idempotencyKey;
        }
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
