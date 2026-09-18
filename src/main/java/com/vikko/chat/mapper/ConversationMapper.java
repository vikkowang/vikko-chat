package com.vikko.chat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会话记忆 Mapper。直接查 Spring AI 的 {@code SPRING_AI_CHAT_MEMORY} 表,
 * 用于按最近活跃时间给会话列表排序(底层 {@code ChatMemoryRepository} 的列 id 接口无排序)。
 */
@Mapper
public interface ConversationMapper {

    @Select("SELECT conversation_id FROM SPRING_AI_CHAT_MEMORY "
            + "GROUP BY conversation_id "
            + "ORDER BY MAX(`timestamp`) DESC "
            + "LIMIT #{size} OFFSET #{offset}")
    List<String> findConversationIdsOrderByRecent(@Param("offset") int offset, @Param("size") int size);
}
