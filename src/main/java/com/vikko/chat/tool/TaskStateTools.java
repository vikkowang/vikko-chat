package com.vikko.chat.tool;

import com.vikko.chat.chat.ConversationContext;
import com.vikko.chat.mapper.TaskStateMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 任务进度工具(结构化笔记):把「任务进行到哪一步」持久化到 task_state 表,
 * 上下文被压缩/重置后仍能读回,解决长任务「失忆」。
 */
@Component
public class TaskStateTools {

    private final TaskStateMapper taskStateMapper;

    public TaskStateTools(TaskStateMapper taskStateMapper) {
        this.taskStateMapper = taskStateMapper;
    }

    @Tool(description = "保存当前任务的进度到持久化存储。多步骤任务每完成一步就更新一次,以便上下文重置后仍能恢复进度。progress 用简短的清单式描述,如「已完成检索和计算,正在汇总」。")
    public String saveTaskProgress(String progress) {
        String conversationId = ConversationContext.get();
        taskStateMapper.upsert(conversationId, progress);
        return "已保存进度到会话 " + conversationId;
    }

    @Tool(description = "读取当前任务已保存的进度。用于长任务在上下文重置后恢复状态;无记录时返回提示。")
    public String loadTaskProgress() {
        String conversationId = ConversationContext.get();
        String progress = taskStateMapper.findProgressByConversationId(conversationId);
        return progress == null ? "暂无已保存的进度" : progress;
    }
}
