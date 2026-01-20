-- ======================================
-- Aero-Fin 数据库 Schema
-- ======================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS aero_fin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE aero_fin;

-- ======================================
-- 1. 金融政策表
-- ======================================
CREATE TABLE IF NOT EXISTS financial_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    policy_code VARCHAR(64) NOT NULL UNIQUE COMMENT '政策编码',
    policy_name VARCHAR(255) NOT NULL COMMENT '政策名称',
    policy_type VARCHAR(32) NOT NULL COMMENT '政策类型: LOAN/WAIVER/INTEREST',
    description TEXT COMMENT '政策描述',
    content LONGTEXT COMMENT '政策详细内容',
    conditions JSON COMMENT '适用条件 (JSON格式)',
    effective_date DATE NOT NULL COMMENT '生效日期',
    expire_date DATE COMMENT '失效日期',
    status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_policy_type (policy_type),
    INDEX idx_status (status),
    INDEX idx_effective_date (effective_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='金融政策表';

-- ======================================
-- 2. 会话历史表
-- ======================================
CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_id VARCHAR(64) COMMENT '用户ID',
    message_type VARCHAR(16) NOT NULL COMMENT '消息类型: USER/ASSISTANT/SYSTEM',
    content TEXT NOT NULL COMMENT '消息内容',
    metadata JSON COMMENT '元数据 (工具调用、思考过程等)',
    token_count INT DEFAULT 0 COMMENT 'Token数量',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话历史表';

-- ======================================
-- 3. 罚息减免申请表
-- ======================================
CREATE TABLE IF NOT EXISTS waiver_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    application_no VARCHAR(64) NOT NULL UNIQUE COMMENT '申请编号',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    loan_account VARCHAR(64) NOT NULL COMMENT '贷款账号',
    waiver_amount DECIMAL(15,2) NOT NULL COMMENT '减免金额',
    reason TEXT COMMENT '申请原因',
    status VARCHAR(16) DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    reviewed_at TIMESTAMP NULL COMMENT '审核时间',
    reviewer VARCHAR(64) COMMENT '审核人',
    review_comment TEXT COMMENT '审核意见',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_submitted_at (submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='罚息减免申请表';

-- ======================================
-- 4. 工具调用日志表 (监控用)
-- ======================================
CREATE TABLE IF NOT EXISTS tool_invocation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    session_id VARCHAR(64) COMMENT '会话ID',
    tool_name VARCHAR(64) NOT NULL COMMENT '工具名称',
    parameters JSON COMMENT '工具参数',
    result TEXT COMMENT '执行结果',
    execution_time_ms BIGINT COMMENT '执行耗时(毫秒)',
    status VARCHAR(16) NOT NULL COMMENT '状态: SUCCESS/FAILURE/TIMEOUT',
    error_message TEXT COMMENT '错误信息',
    cache_hit BOOLEAN DEFAULT FALSE COMMENT '是否命中缓存',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_tool_name (tool_name),
    INDEX idx_session_id (session_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具调用日志表';

-- ======================================
-- 初始化测试数据
-- ======================================

-- 插入示例政策数据
INSERT INTO financial_policies (policy_code, policy_name, policy_type, description, content, conditions, effective_date, expire_date, status)
VALUES
('POLICY_LOAN_001', '个人消费贷款政策', 'LOAN', '面向个人消费场景的贷款产品',
 '贷款额度：1万-50万，年利率：3.85%-5.6%，期限：12-60个月',
 '{"minAmount": 10000, "maxAmount": 500000, "minTerm": 12, "maxTerm": 60, "minRate": 0.0385, "maxRate": 0.056}',
 '2024-01-01', NULL, 'ACTIVE'),

('POLICY_LOAN_002', '小微企业经营贷', 'LOAN', '支持小微企业发展的经营性贷款',
 '贷款额度：10万-300万，年利率：4.35%-6.5%，期限：12-36个月',
 '{"minAmount": 100000, "maxAmount": 3000000, "minTerm": 12, "maxTerm": 36, "minRate": 0.0435, "maxRate": 0.065}',
 '2024-01-01', NULL, 'ACTIVE'),

('POLICY_WAIVER_001', '疫情期间罚息减免政策', 'WAIVER', '针对疫情期间受影响客户的罚息减免',
 '符合条件的客户可申请减免50%-100%的罚息',
 '{"eligiblePeriod": "2020-01-01 to 2023-12-31", "maxWaiverRate": 1.0, "minWaiverRate": 0.5}',
 '2020-01-01', '2025-12-31', 'ACTIVE'),

('POLICY_INTEREST_001', '提前还款利息优惠', 'INTEREST', '提前还款享受利息减免',
 '提前还款可减免剩余期限的20%利息',
 '{"waiverRate": 0.2, "minPrepayAmount": 10000}',
 '2024-01-01', NULL, 'ACTIVE');

-- 插入示例会话数据（用于测试）
INSERT INTO conversations (session_id, user_id, message_type, content, token_count)
VALUES
('test-session-001', 'user-001', 'USER', '我想申请一笔消费贷款，需要20万', 150),
('test-session-001', 'user-001', 'ASSISTANT', '好的，根据我们的个人消费贷款政策，20万的额度是在范围内的。请问您希望的还款期限是多久？', 200);

COMMIT;
