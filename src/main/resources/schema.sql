-- 用户状态表:由 spring.sql.init(见 application.yml)在启动时自动创建
CREATE TABLE IF NOT EXISTS user_status (
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    status   VARCHAR(16) NOT NULL COMMENT '账户状态:ACTIVE(正常)/INACTIVE(停用)/BANNED(封禁)',
    PRIMARY KEY (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 任务进度表(结构化笔记):按会话保存「任务进行到哪一步」,长任务上下文重置后可恢复
CREATE TABLE IF NOT EXISTS task_state (
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话 id',
    progress        TEXT COMMENT '任务进度(清单式笔记)',
    PRIMARY KEY (conversation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
