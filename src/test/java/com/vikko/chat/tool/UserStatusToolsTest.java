package com.vikko.chat.tool;

import com.vikko.chat.mapper.UserStatusMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 用户状态工具的 golden 断言:用 Mockito 打桩 Mapper,不碰 MySQL,断言工具的返回文案。
 */
class UserStatusToolsTest {

    @Test
    void queryUserStatusFound() {
        UserStatusMapper mapper = mock(UserStatusMapper.class);
        when(mapper.findStatusByUsername("alice")).thenReturn("ACTIVE");
        UserStatusTools tools = new UserStatusTools(mapper);

        assertEquals("alice 的状态: 正常", tools.queryUserStatus("alice"));
    }

    @Test
    void queryUserStatusNotFound() {
        UserStatusMapper mapper = mock(UserStatusMapper.class);
        when(mapper.findStatusByUsername("nobody")).thenReturn(null);
        UserStatusTools tools = new UserStatusTools(mapper);

        assertEquals("未找到用户: nobody", tools.queryUserStatus("nobody"));
    }

    @Test
    void updateUserStatusOk() {
        UserStatusMapper mapper = mock(UserStatusMapper.class);
        when(mapper.updateStatusByUsername("bob", "BANNED")).thenReturn(1);
        UserStatusTools tools = new UserStatusTools(mapper);

        assertEquals("bob 的状态已更新为: 封禁", tools.updateUserStatus("bob", "BANNED"));
    }

    @Test
    void updateUserStatusInvalid() {
        UserStatusMapper mapper = mock(UserStatusMapper.class);
        UserStatusTools tools = new UserStatusTools(mapper);

        String result = tools.updateUserStatus("bob", "BAD");
        assertTrue(result.startsWith("不支持的状态: BAD"), "实际: " + result);
    }

    @Test
    void updateUserStatusNotFound() {
        UserStatusMapper mapper = mock(UserStatusMapper.class);
        when(mapper.updateStatusByUsername("nobody", "ACTIVE")).thenReturn(0);
        UserStatusTools tools = new UserStatusTools(mapper);

        assertEquals("未找到用户: nobody", tools.updateUserStatus("nobody", "ACTIVE"));
    }
}
