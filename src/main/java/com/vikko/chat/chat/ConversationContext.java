package com.vikko.chat.chat;

/**
 * 当前请求的会话上下文(ThreadLocal 持有)。
 *
 * <p>工具方法({@code @Tool})本身拿不到 conversationId,但「任务进度」这类按会话隔离的状态又需要它。
 * 由 {@link ChatService} 在每次请求开始时 set、结束时 clear,工具内部通过 {@link #get()} 读取当前会话 id。
 */
public final class ConversationContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private ConversationContext() {
    }

    public static void set(String conversationId) {
        HOLDER.set(conversationId);
    }

    public static String get() {
        String value = HOLDER.get();
        return value == null ? "default" : value;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
