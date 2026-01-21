package com.aerofin.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码规范
 * <p>
 * 错误码分类：
 * - 1xxxx: 通用错误（参数、认证、限流）
 * - 2xxxx: 业务错误（会话、对话）
 * - 3xxxx: Agent相关
 * - 4xxxx: 工具调用
 * - 5xxxx: RAG/向量检索
 * - 6xxxx: LLM相关
 *
 * @author Aero-Fin Team
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ========== 1xxxx 通用错误 ==========
    SUCCESS(10000, "成功"),
    PARAM_ERROR(10001, "参数错误"),
    SYSTEM_ERROR(10002, "系统异常"),
    UNAUTHORIZED(10003, "未授权"),
    FORBIDDEN(10004, "无权限"),
    RATE_LIMIT_EXCEEDED(10005, "请求过于频繁"),

    // ========== 2xxxx 业务错误 ==========
    SESSION_NOT_FOUND(20001, "会话不存在"),
    SESSION_EXPIRED(20002, "会话已过期"),
    SNAPSHOT_NOT_FOUND(20003, "快照不存在或已过期"),
    CONVERSATION_SAVE_FAILED(20004, "会话保存失败"),
    CONVERSATION_HISTORY_EMPTY(20005, "对话历史为空"),

    // ========== 3xxxx Agent相关 ==========
    AGENT_NOT_FOUND(30001, "Agent不存在"),
    AGENT_EXECUTION_FAILED(30002, "Agent执行失败"),
    INTENT_RECOGNITION_FAILED(30003, "意图识别失败"),
    MULTI_AGENT_ORCHESTRATION_FAILED(30004, "多Agent编排失败"),
    REFLECT_AGENT_FAILED(30005, "反思Agent执行失败"),

    // ========== 4xxxx 工具调用 ==========
    TOOL_INVOCATION_FAILED(40001, "工具调用失败"),
    TOOL_TIMEOUT(40002, "工具调用超时"),
    TOOL_CACHE_ERROR(40003, "工具缓存异常"),
    INVALID_LOAN_PARAMS(40004, "贷款参数无效"),

    // ========== 5xxxx RAG/向量检索 ==========
    VECTOR_SEARCH_FAILED(50001, "向量检索失败"),
    MILVUS_CONNECTION_ERROR(50002, "Milvus连接异常"),
    EMBEDDING_FAILED(50003, "向量化失败"),
    RAG_CONTEXT_EMPTY(50004, "未检索到相关内容"),

    // ========== 6xxxx LLM相关 ==========
    LLM_CALL_FAILED(60001, "LLM调用失败"),
    LLM_TIMEOUT(60002, "LLM调用超时"),
    LLM_RATE_LIMIT(60003, "LLM限流"),
    LLM_QUOTA_EXCEEDED(60004, "LLM配额超限");

    private final Integer code;
    private final String message;
}
