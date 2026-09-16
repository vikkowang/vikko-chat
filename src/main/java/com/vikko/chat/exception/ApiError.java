package com.vikko.chat.exception;

import java.time.Instant;

/**
 * 统一错误返回结构。
 */
public record ApiError(int code, String message, Instant timestamp, String path) {
}
