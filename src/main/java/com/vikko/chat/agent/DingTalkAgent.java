package com.vikko.chat.agent;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 钉钉 agent:一个「钉钉文档助手」,内部挂全部钉钉文档工具(40 个)。
 * 这是最能体现 agent 价值的场景——它要根据指令自己挑工具、规划多步操作。
 */
@Component
public class DingTalkAgent implements Agent {

    private final ChatClient client;
    private final ToolCallbackProvider dingTalkProvider;

    public DingTalkAgent(ChatClient.Builder builder, McpSyncClient dingTalkMcpClient) {
        this.client = builder.build();
        this.dingTalkProvider = new SyncMcpToolCallbackProvider(dingTalkMcpClient);
    }

    @Tool(description = "操作钉钉文档/知识库:搜索、读取、创建、编辑、整理、移动、删除、权限管理等。适合任何涉及钉钉文档的任务。")
    public String operateDocuments(String instruction) {
        return cap(client.prompt()
                .system("""
                        你是钉钉文档助手,负责对钉钉文档和知识库进行操作。
                        要求:
                        1. 对复杂任务,先规划步骤再逐步执行(例如「整理某文档并新建一篇」需要先搜、再读、再建);
                        2. 会改变数据/权限的操作要谨慎,先确认目标再动手;
                        3. 操作完成后,向用户报告做了什么、结果如何。
                        """)
                .user(instruction)
                .toolCallbacks(dingTalkProvider)
                .call()
                .content());
    }
}
