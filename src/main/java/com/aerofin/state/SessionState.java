package com.aerofin.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 会话状态
 * <p>
 * 管理当前会话的上下文信息、对话状态、槽位填充等
 * <p>
 * 核心功能：
 * 1. 对话流程管理（DialogFlow）
 * 2. 槽位填充（Slot Filling）
 * 3. 上下文变量存储
 * 4. 会话快照与恢复
 * <p>
 * 面试亮点：
 * - 有限状态机（FSM）管理对话流程
 * - 槽位填充机制（类似 Rasa）
 * - 支持会话暂停与恢复（断点续聊）
 *
 * @author Aero-Fin Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionState {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 当前对话状态
     * <p>
     * 可能的状态：
     * - IDLE: 空闲，等待用户输入
     * - COLLECTING_INFO: 收集信息中（如贷款金额、期限）
     * - TOOL_CALLING: 工具调用中
     * - WAITING_CONFIRMATION: 等待用户确认
     * - COMPLETED: 任务完成
     */
    @Builder.Default
    private DialogState currentState = DialogState.IDLE;

    /**
     * 当前任务类型
     * <p>
     * 可能的任务：
     * - LOAN_CALCULATION: 贷款计算
     * - POLICY_QUERY: 政策查询
     * - WAIVER_APPLICATION: 罚息减免申请
     * - GENERAL_QA: 一般问答
     */
    private String currentTask;

    /**
     * 槽位填充状态
     * <p>
     * 例如贷款计算任务需要的槽位：
     * - principal: 贷款本金
     * - annualRate: 年利率
     * - termMonths: 贷款期限
     * <p>
     * Key: 槽位名称, Value: 槽位值
     */
    @Builder.Default
    private Map<String, Object> slots = new HashMap<>();

    /**
     * 上下文变量
     * <p>
     * 存储会话期间的临时变量，如：
     * - lastQueryResult: 上次查询结果
     * - userIntent: 用户意图
     * - emotionState: 情感状态
     */
    @Builder.Default
    private Map<String, Object> contextVariables = new HashMap<>();

    /**
     * 对话轮次计数
     */
    @Builder.Default
    private Integer turnCount = 0;

    /**
     * 会话创建时间
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 最后更新时间
     */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * 会话是否已暂停（用于断点续聊）
     */
    @Builder.Default
    private Boolean paused = false;

    /**
     * 会话快照（用于恢复）
     */
    private String snapshot;

    /**
     * 检查槽位是否已填充完整
     *
     * @param requiredSlots 必需的槽位列表
     * @return 是否所有必需槽位都已填充
     */
    public boolean isSlotsFilled(String... requiredSlots) {
        for (String slot : requiredSlots) {
            if (!slots.containsKey(slot) || slots.get(slot) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取缺失的槽位
     */
    public String[] getMissingSlots(String... requiredSlots) {
        return java.util.Arrays.stream(requiredSlots)
                .filter(slot -> !slots.containsKey(slot) || slots.get(slot) == null)
                .toArray(String[]::new);
    }

    /**
     * 设置槽位值
     */
    public void setSlot(String slotName, Object value) {
        this.slots.put(slotName, value);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 清空槽位
     */
    public void clearSlots() {
        this.slots.clear();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新对话状态
     */
    public void updateState(DialogState newState) {
        this.currentState = newState;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 增加对话轮次
     */
    public void incrementTurn() {
        this.turnCount++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 创建会话快照（用于断点续聊）
     */
    public String createSnapshot() {
        // 使用 Jackson 将完整 SessionState 序列化为 JSON，便于后续精确恢复
        ObjectMapper mapper = new ObjectMapper();
        try {
            this.snapshot = mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            // 序列化失败时降级为简单字符串，避免影响主流程
            this.snapshot = String.format("{\"sessionId\":\"%s\",\"state\":\"%s\"}", sessionId, currentState);
        }
        return this.snapshot;
    }

    /**
     * 从快照恢复会话
     */
    public static SessionState fromSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return SessionState.builder().build();
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            SessionState state = mapper.readValue(snapshot, SessionState.class);
            // 恢复后默认认为不再处于暂停状态
            state.setPaused(false);
            state.setSnapshot(snapshot);
            return state;
        } catch (Exception e) {
            // 反序列化失败时返回一个基础状态，避免整体流程崩溃
            return SessionState.builder()
                    .paused(false)
                    .build();
        }
    }

    /**
     * 对话状态枚举
     */
    public enum DialogState {
        IDLE("空闲"),
        COLLECTING_INFO("收集信息中"),
        TOOL_CALLING("工具调用中"),
        WAITING_CONFIRMATION("等待确认"),
        COMPLETED("已完成"),
        ERROR("错误状态");

        private final String description;

        DialogState(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
