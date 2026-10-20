-- 环信智能客服系统 · 数据库初始化脚本
-- MySQL 8.0
-- 数据库名：easemob_customer

CREATE DATABASE IF NOT EXISTS easemob_customer
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE easemob_customer;

-- 客户端会话编码（防止以 latin1 客户端执行时中文种子数据乱码）
SET NAMES utf8mb4;

-- 组（AI组 / 坐席技能组 / 用户组）
CREATE TABLE IF NOT EXISTS cs_group (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL COMMENT 'A组/B组/AI组',
    type        VARCHAR(16)  NOT NULL COMMENT 'AI/AGENT/USER',
    parent_id   BIGINT       DEFAULT NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_type_status (type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号分组';

-- 账号（AI / 用户 / 坐席统一表）
CREATE TABLE IF NOT EXISTS cs_account (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    easemob_id    VARCHAR(64)  NOT NULL COMMENT '环信用户ID',
    type          VARCHAR(8)   NOT NULL COMMENT 'AI/USER/AGENT',
    group_id      BIGINT       DEFAULT NULL,
    nickname      VARCHAR(64),
    avatar        VARCHAR(255),
    max_sessions  INT          DEFAULT 5 COMMENT '坐席接待上限',
    status        TINYINT      NOT NULL DEFAULT 1,
    ext_json      JSON         DEFAULT NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_easemob (easemob_id),
    KEY idx_type_group (type, group_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号表';

-- 会话（群组即会话）
CREATE TABLE IF NOT EXISTS cs_session (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id             VARCHAR(64) NOT NULL COMMENT '环信群组ID',
    user_account_id      VARCHAR(64) NOT NULL,
    ai_account_id        VARCHAR(64) NOT NULL,
    agent_account_id     VARCHAR(64) DEFAULT NULL,
    status               VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    current_handler_type VARCHAR(8)  NOT NULL DEFAULT 'AI',
    channel              VARCHAR(16),
    skill_group_id       BIGINT,
    transfer_reason      VARCHAR(32) DEFAULT NULL,
    fail_count           INT NOT NULL DEFAULT 0 COMMENT 'AI连续失败次数',
    first_response_ms    INT COMMENT 'AI/人工首响耗时',
    last_msg_time        DATETIME,
    close_reason         VARCHAR(32),
    create_time          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group (group_id),
    KEY idx_user_status (user_account_id, status),
    KEY idx_last_msg (status, last_msg_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 消息记录
CREATE TABLE IF NOT EXISTS cs_message (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id  BIGINT      NOT NULL,
    msg_id      VARCHAR(64) NOT NULL COMMENT '环信消息ID（幂等）',
    sender_id   VARCHAR(64) NOT NULL,
    sender_type VARCHAR(8)  NOT NULL COMMENT 'USER/AI/AGENT/SYSTEM',
    msg_type    VARCHAR(8)  NOT NULL COMMENT 'TXT/IMG/FILE',
    content     TEXT,
    biz_type    VARCHAR(24) DEFAULT NULL COMMENT 'TRANSFERRED/QUEUE_UPDATE/CLOSED',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_msg (msg_id),
    KEY idx_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息记录';

-- 熟客关系
CREATE TABLE IF NOT EXISTS cs_familiar_relation (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         VARCHAR(64) NOT NULL,
    agent_id        VARCHAR(64) NOT NULL,
    serve_count     INT NOT NULL DEFAULT 1,
    last_serve_time DATETIME NOT NULL,
    UNIQUE KEY uk_user_agent (user_id, agent_id),
    KEY idx_agent_time (agent_id, last_serve_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='熟客关系';

-- 转人工记录
CREATE TABLE IF NOT EXISTS cs_transfer_record (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id  BIGINT NOT NULL,
    from_type   VARCHAR(8)  NOT NULL DEFAULT 'AI',
    to_agent_id VARCHAR(64),
    `trigger`   VARCHAR(16) NOT NULL COMMENT 'KEYWORD/AI_TRANSFER/FAILOVER/MANUAL/VIP',
    result      VARCHAR(16) NOT NULL COMMENT 'SUCCESS/QUEUED/FAILED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_session (session_id),
    KEY idx_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转人工记录';

-- AI 厂家配置
CREATE TABLE IF NOT EXISTS cs_ai_provider (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code VARCHAR(32) NOT NULL,
    name          VARCHAR(64) NOT NULL,
    base_url      VARCHAR(255) NOT NULL,
    app_key       VARCHAR(255),
    app_secret    VARCHAR(255) COMMENT 'AES 加密存储',
    timeout_ms    INT NOT NULL DEFAULT 8000,
    retry_times   INT NOT NULL DEFAULT 1,
    weight        INT NOT NULL DEFAULT 100,
    status        TINYINT NOT NULL DEFAULT 1,
    extra_json    JSON,
    create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_status_weight (status, weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI厂家配置';

-- 分配策略配置（单行全局 + 组覆盖 JSON）
CREATE TABLE IF NOT EXISTS cs_assignment_strategy (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    scope       VARCHAR(16) NOT NULL COMMENT 'GLOBAL/GROUP',
    group_id    BIGINT DEFAULT NULL,
    config_json JSON NOT NULL,
    update_by   VARCHAR(64),
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_scope_group (scope, group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分配策略配置';

-- 满意度评价
CREATE TABLE IF NOT EXISTS cs_evaluate (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id  BIGINT NOT NULL UNIQUE,
    score       TINYINT NOT NULL COMMENT '1-5',
    tags        VARCHAR(255),
    comment     VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_score_time (score, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满意度评价';

-- 快捷回复
CREATE TABLE IF NOT EXISTS cs_quick_reply (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_name  VARCHAR(64),
    title       VARCHAR(64) NOT NULL,
    content     TEXT NOT NULL,
    status      TINYINT DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快捷回复';

-- 转人工词表
CREATE TABLE IF NOT EXISTS cs_transfer_word (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    word        VARCHAR(64) NOT NULL COMMENT '支持 re: 前缀正则',
    status      TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_word (word),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转人工词表';

-- 操作日志
CREATE TABLE IF NOT EXISTS cs_op_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    operator    VARCHAR(64) NOT NULL,
    action      VARCHAR(64) NOT NULL,
    detail      JSON,
    ip          VARCHAR(45),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_op_time (operator, create_time),
    KEY idx_action_time (action, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- 日统计（看板）
CREATE TABLE IF NOT EXISTS cs_stat_daily (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_date             DATE NOT NULL,
    session_count         INT NOT NULL DEFAULT 0,
    ai_resolved           INT NOT NULL DEFAULT 0,
    transferred           INT NOT NULL DEFAULT 0,
    avg_first_response_ms INT,
    avg_score             DECIMAL(3,2),
    agent_id              VARCHAR(64) DEFAULT NULL COMMENT 'NULL=全局',
    UNIQUE KEY uk_date_agent (stat_date, agent_id),
    KEY idx_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日统计';

-- 初始数据
INSERT INTO cs_group (name, type, status) VALUES
('AI默认组', 'AI', 1),
('坐席默认组', 'AGENT', 1),
('用户默认组', 'USER', 1)
ON DUPLICATE KEY UPDATE name = name;

INSERT INTO cs_assignment_strategy (scope, group_id, config_json) VALUES
('GLOBAL', NULL, '{"defaultStrategy":"FAMILIAR_FIRST","familiarValidDays":30,"queueLimit":50,"noAgentPolicy":"QUEUE_WITH_AI","maxSessionsPerAgent":5,"groupOverrides":[]}')
ON DUPLICATE KEY UPDATE config_json = VALUES(config_json);

INSERT INTO cs_transfer_word (word, status) VALUES
('转人工', 1),
('人工客服', 1),
('客服', 1),
('投诉', 1),
('找人工', 1)
ON DUPLICATE KEY UPDATE status = status;
