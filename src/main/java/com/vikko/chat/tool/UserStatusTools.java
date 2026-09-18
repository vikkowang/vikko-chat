package com.vikko.chat.tool;

import com.vikko.chat.mapper.UserStatusMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 用户状态工具:查/改 user_status 表。被「用户管理 agent」和 MCP Server 共用。
 */
@Component
public class UserStatusTools {

    private final UserStatusMapper userStatusMapper;

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
