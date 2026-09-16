package com.vikko.chat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户状态 Mapper。
 *
 * <p>这里返回状态名的字符串,再在业务层映射到 {@link com.vikko.chat.tool.UserStatus}。
 * 也可以直接把返回值声明为 {@code UserStatus} 枚举(MyBatis 默认用 {@code EnumTypeHandler} 按名字映射)。
 */
@Mapper
public interface UserStatusMapper {

    @Select("SELECT status FROM user_status WHERE username = #{username}")
    String findStatusByUsername(@Param("username") String username);

    @Update("UPDATE user_status SET status = #{status} WHERE username = #{username}")
    int updateStatusByUsername(@Param("username") String username, @Param("status") String status);
}
