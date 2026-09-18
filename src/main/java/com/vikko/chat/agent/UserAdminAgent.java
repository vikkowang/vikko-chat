package com.vikko.chat.agent;

import com.vikko.chat.tool.UserStatusTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 用户管理 agent:一个「用户账户管理员」,内部挂 queryUserStatus + updateUserStatus 工具。
 * 改状态前会先查确认,体现 agent 的谨慎与多步。
 */
@Component
public class UserAdminAgent implements Agent {

    private final ChatClient client;
    private final UserStatusTools userStatusTools;

    public UserAdminAgent(ChatClient.Builder builder, UserStatusTools userStatusTools) {
        this.client = builder.build();
        this.userStatusTools = userStatusTools;
    }

    @Tool(description = "查询或修改用户账户状态(正常/停用/封禁)。修改前会先查询确认当前状态,修改后报告变更结果。")
    public String manageUserStatus(String instruction) {
        return client.prompt()
                .system("""
                        你是用户账户管理员,负责查询和修改用户状态(正常/停用/封禁)。
                        要求:
                        1. 修改状态前,先查询该用户当前状态以确认;
                        2. 封禁等敏感操作要谨慎,明确报告变更前后的状态;
                        3. 找不到用户时,如实告知。
                        """)
                .user(instruction)
                .tools(userStatusTools)
                .call()
                .content();
    }
}
