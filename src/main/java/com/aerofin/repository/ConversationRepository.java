package com.aerofin.repository;

import com.aerofin.model.entity.Conversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话历史数据访问层
 * <p>
 * 管理用户与 Agent 的对话历史，用于：
 * 1. 保存完整的对话上下文
 * 2. 支持会话恢复
 * 3. 监控和分析用户行为
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ConversationRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Conversation> CONVERSATION_ROW_MAPPER = new ConversationRowMapper();

    /**
     * 保存会话消息
     */
    public void save(Conversation conversation) {
        String sql = "INSERT INTO conversations " +
                "(session_id, user_id, message_type, content, metadata, token_count, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                conversation.getSessionId(),
                conversation.getUserId(),
                conversation.getMessageType(),
                conversation.getContent(),
                conversation.getMetadata(),
                conversation.getTokenCount(),
                Timestamp.valueOf(LocalDateTime.now())
        );
    }

    /**
     * 根据会话ID查询历史消息
     * <p>
     * 按照创建时间排序，用于恢复对话上下文
     *
     * @param sessionId 会话ID
     * @param limit     限制数量（用于滑动窗口）
     */
    public List<Conversation> findBySessionId(String sessionId, int limit) {
        String sql = "SELECT * FROM conversations " +
                "WHERE session_id = ? " +
                "ORDER BY created_at DESC " +
                "LIMIT ?";
        return jdbcTemplate.query(sql, CONVERSATION_ROW_MAPPER, sessionId, limit);
    }

    /**
     * 获取会话的完整历史（按时间升序）
     */
    public List<Conversation> findBySessionIdOrdered(String sessionId) {
        String sql = "SELECT * FROM conversations " +
                "WHERE session_id = ? " +
                "ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, CONVERSATION_ROW_MAPPER, sessionId);
    }

    /**
     * 根据用户ID查询所有会话
     */
    public List<Conversation> findByUserId(String userId, int limit) {
        String sql = "SELECT * FROM conversations " +
                "WHERE user_id = ? " +
                "ORDER BY created_at DESC " +
                "LIMIT ?";
        return jdbcTemplate.query(sql, CONVERSATION_ROW_MAPPER, userId, limit);
    }

    /**
     * 删除旧的会话记录（数据清理）
     *
     * @param sessionId 会话ID
     * @param keepCount 保留数量
     */
    public void deleteOldConversations(String sessionId, int keepCount) {
        String sql = "DELETE FROM conversations WHERE session_id = ? " +
                "AND id NOT IN (SELECT id FROM conversations WHERE session_id = ? " +
                "ORDER BY created_at DESC LIMIT ?)";
        jdbcTemplate.update(sql, sessionId, sessionId, keepCount);
    }

    /**
     * Conversation RowMapper
     */
    private static class ConversationRowMapper implements RowMapper<Conversation> {
        @Override
        public Conversation mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Conversation.builder()
                    .id(rs.getLong("id"))
                    .sessionId(rs.getString("session_id"))
                    .userId(rs.getString("user_id"))
                    .messageType(rs.getString("message_type"))
                    .content(rs.getString("content"))
                    .metadata(rs.getString("metadata"))
                    .tokenCount(rs.getInt("token_count"))
                    .createdAt(rs.getTimestamp("created_at") != null ?
                            rs.getTimestamp("created_at").toLocalDateTime() : null)
                    .build();
        }
    }
}
