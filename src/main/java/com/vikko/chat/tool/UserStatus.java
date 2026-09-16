package com.vikko.chat.tool;

/**
 * 用户账户状态枚举。
 *
 * @param description 状态的中文描述,用于工具返回给模型的可读文本
 */
public enum UserStatus {

    ACTIVE("正常"),
    INACTIVE("停用"),
    BANNED("封禁");

    private final String description;

    UserStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
