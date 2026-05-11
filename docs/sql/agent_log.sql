-- =============================================================================
-- Agent AI 请求日志表
--
-- 设计：一次 /agent/ask 调用 = 主表 1 行 + 明细表 N 行（N 等于 AI HTTP 调用次数）
--   * agent_request_log     —— 业务粒度，方便按"用户问了什么"做检索/统计
--   * agent_ai_call_log     —— 调用粒度，方便排查每一轮 AI 回了什么、token 消耗
--
-- 通过 request_id 串联。明细表也额外冗余一份索引在 request_log_id 上，
-- 这样不用 JOIN 也能直接按主表外键拉所有 call。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `agent_request_log` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `request_id`      VARCHAR(64)     NOT NULL                COMMENT '请求唯一 ID（UUID，与明细表关联）',
    `agent_name`      VARCHAR(32)              DEFAULT NULL   COMMENT '处理本次请求的 agent 名（database / alias / ...）',
    `user_message`    TEXT            NOT NULL                COMMENT '用户原始输入',
    `model`           VARCHAR(64)              DEFAULT NULL   COMMENT '使用的模型名',
    `reply`           MEDIUMTEXT                              COMMENT 'AI 最终自然语言回复',
    `trace`           MEDIUMTEXT                              COMMENT '工具调用轨迹（JSON 数组）',
    `iterations`      INT             NOT NULL DEFAULT 0      COMMENT 'Agent 主循环实际迭代次数',
    `tool_call_count` INT             NOT NULL DEFAULT 0      COMMENT '总工具调用次数',
    `total_tokens`    INT                      DEFAULT NULL   COMMENT '本次 ask 累计消耗 token',
    `status`          VARCHAR(16)     NOT NULL                COMMENT 'SUCCESS / ERROR / MAX_ITERATIONS',
    `error_message`   TEXT                                    COMMENT '失败原因（status=ERROR 时填）',
    `duration_ms`     BIGINT          NOT NULL DEFAULT 0      COMMENT '总耗时（毫秒）',
    `client_ip`       VARCHAR(64)              DEFAULT NULL   COMMENT '客户端 IP',
    `create_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_request_id`  (`request_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_status`      (`status`),
    KEY `idx_agent_name`  (`agent_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent AI 请求日志（业务粒度，每次 ask 一行）';


CREATE TABLE IF NOT EXISTS `agent_ai_call_log` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `request_log_id`    BIGINT UNSIGNED          DEFAULT NULL   COMMENT '主表 agent_request_log.id（异步写入，可能延迟）',
    `request_id`        VARCHAR(64)     NOT NULL                COMMENT '请求唯一 ID（与主表关联，外键软关联）',
    `agent_name`        VARCHAR(32)              DEFAULT NULL   COMMENT '产生本条调用的 agent 名（router / database / alias）',
    `iteration_index`   INT             NOT NULL                COMMENT '在主循环里的轮次（从 0 开始）',
    `model`             VARCHAR(64)              DEFAULT NULL   COMMENT '请求使用的模型',
    `request_body`      MEDIUMTEXT                              COMMENT '完整请求体 JSON（含 messages / tools）',
    `response_body`     MEDIUMTEXT                              COMMENT '完整响应体 JSON',
    `http_status`       INT                      DEFAULT NULL   COMMENT 'HTTP 状态码',
    `prompt_tokens`     INT                      DEFAULT NULL   COMMENT 'usage.prompt_tokens',
    `completion_tokens` INT                      DEFAULT NULL   COMMENT 'usage.completion_tokens',
    `total_tokens`      INT                      DEFAULT NULL   COMMENT 'usage.total_tokens',
    `tool_call_count`   INT             NOT NULL DEFAULT 0      COMMENT '本轮 AI 触发了几个 tool_call',
    `finish_reason`     VARCHAR(32)              DEFAULT NULL   COMMENT 'choices[0].finish_reason',
    `duration_ms`       BIGINT          NOT NULL DEFAULT 0      COMMENT 'HTTP 调用耗时（毫秒）',
    `error_message`     TEXT                                    COMMENT '失败原因（异常时填）',
    `create_time`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_request_id`     (`request_id`),
    KEY `idx_request_log_id` (`request_log_id`),
    KEY `idx_create_time`    (`create_time`),
    KEY `idx_agent_name`     (`agent_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent 单次 AI HTTP 调用日志（调用粒度，每轮一行）';

-- 已建过表的话执行下面两行补字段：
-- ALTER TABLE `agent_request_log` ADD COLUMN `agent_name` VARCHAR(32) DEFAULT NULL COMMENT '处理本次请求的 agent 名' AFTER `request_id`, ADD KEY `idx_agent_name` (`agent_name`);
-- ALTER TABLE `agent_ai_call_log` ADD COLUMN `agent_name` VARCHAR(32) DEFAULT NULL COMMENT '产生本条调用的 agent 名' AFTER `request_id`, ADD KEY `idx_agent_name` (`agent_name`);
