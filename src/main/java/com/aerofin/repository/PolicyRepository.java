package com.aerofin.repository;

import com.aerofin.model.entity.Policy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * 政策数据访问层
 * <p>
 * 使用 JdbcTemplate 进行数据库操作
 * <p>
 * 面试亮点：
 * 1. 使用 Spring JdbcTemplate，轻量高效
 * 2. 支持政策类型过滤查询
 * 3. 支持全文搜索（LIKE 查询）
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PolicyRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Policy> POLICY_ROW_MAPPER = new PolicyRowMapper();

    /**
     * 根据政策编码查询
     */
    public Optional<Policy> findByPolicyCode(String policyCode) {
        String sql = "SELECT * FROM financial_policies WHERE policy_code = ? AND status = 'ACTIVE'";
        List<Policy> results = jdbcTemplate.query(sql, POLICY_ROW_MAPPER, policyCode);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据政策类型查询
     *
     * @param policyType 政策类型: LOAN/WAIVER/INTEREST
     */
    public List<Policy> findByPolicyType(String policyType) {
        String sql = "SELECT * FROM financial_policies WHERE policy_type = ? AND status = 'ACTIVE' " +
                "ORDER BY effective_date DESC";
        return jdbcTemplate.query(sql, POLICY_ROW_MAPPER, policyType);
    }

    /**
     * 关键词搜索政策
     * <p>
     * 在政策名称、描述、内容中搜索关键词
     */
    public List<Policy> searchByKeyword(String keyword) {
        String sql = "SELECT * FROM financial_policies " +
                "WHERE status = 'ACTIVE' " +
                "AND (policy_name LIKE ? OR description LIKE ? OR content LIKE ?) " +
                "ORDER BY effective_date DESC " +
                "LIMIT 10";
        String searchPattern = "%" + keyword + "%";
        return jdbcTemplate.query(sql, POLICY_ROW_MAPPER, searchPattern, searchPattern, searchPattern);
    }

    /**
     * 查询所有有效政策
     */
    public List<Policy> findAllActive() {
        String sql = "SELECT * FROM financial_policies WHERE status = 'ACTIVE' " +
                "ORDER BY policy_type, effective_date DESC";
        return jdbcTemplate.query(sql, POLICY_ROW_MAPPER);
    }

    /**
     * 获取政策总数
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM financial_policies WHERE status = 'ACTIVE'";
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    /**
     * Policy RowMapper
     */
    private static class PolicyRowMapper implements RowMapper<Policy> {
        @Override
        public Policy mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Policy.builder()
                    .id(rs.getLong("id"))
                    .policyCode(rs.getString("policy_code"))
                    .policyName(rs.getString("policy_name"))
                    .policyType(rs.getString("policy_type"))
                    .description(rs.getString("description"))
                    .content(rs.getString("content"))
                    .conditions(rs.getString("conditions"))
                    .effectiveDate(rs.getDate("effective_date") != null ?
                            rs.getDate("effective_date").toLocalDate() : null)
                    .expireDate(rs.getDate("expire_date") != null ?
                            rs.getDate("expire_date").toLocalDate() : null)
                    .status(rs.getString("status"))
                    .createdAt(rs.getTimestamp("created_at") != null ?
                            rs.getTimestamp("created_at").toLocalDateTime() : null)
                    .updatedAt(rs.getTimestamp("updated_at") != null ?
                            rs.getTimestamp("updated_at").toLocalDateTime() : null)
                    .build();
        }
    }
}
