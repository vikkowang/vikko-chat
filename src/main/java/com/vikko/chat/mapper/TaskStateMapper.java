package com.vikko.chat.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 任务进度(结构化笔记)Mapper。按会话保存「任务进行到哪一步」,供长任务在上下文重置后恢复。
 */
@Mapper
public interface TaskStateMapper {

    @Select("SELECT progress FROM task_state WHERE conversation_id = #{conversationId}")
    String findProgressByConversationId(@Param("conversationId") String conversationId);

    @Insert("INSERT INTO task_state (conversation_id, progress) VALUES (#{conversationId}, #{progress}) "
            + "ON DUPLICATE KEY UPDATE progress = #{progress}")
    int upsert(@Param("conversationId") String conversationId, @Param("progress") String progress);
}
