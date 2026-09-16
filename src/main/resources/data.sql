-- 初始化示例用户状态(幂等:重复启动不会报错)
INSERT IGNORE INTO user_status (username, status) VALUES
    ('alice', 'ACTIVE'),
    ('bob',   'INACTIVE'),
    ('carol', 'BANNED');
