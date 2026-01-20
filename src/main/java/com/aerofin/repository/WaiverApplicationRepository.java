package com.aerofin.repository;

import com.aerofin.model.entity.WaiverApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 罚息减免申请数据访问层
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class WaiverApplicationRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<WaiverApplication> WAIVER_ROW_MAPPER = new WaiverRowMapper();

    /**
     * 保存减免申请
     */
    public WaiverApplication save(WaiverApplication application) {
        String sql = "INSERT INTO waiver_applications " +
                "(application_no, user_id, loan_account, waiver_amount, reason, status, submitted_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                application.getApplicationNo(),
                application.getUserId(),
                application.getLoanAccount(),
                application.getWaiverAmount(),
                application.getReason(),
                "PENDING",
                Timestamp.valueOf(LocalDateTime.now())
        );
        return application;
    }

    /**
     * 根据申请编号查询
     */
    public Optional<WaiverApplication> findByApplicationNo(String applicationNo) {
        String sql = "SELECT * FROM waiver_applications WHERE application_no = ?";
        List<WaiverApplication> results = jdbcTemplate.query(sql, WAIVER_ROW_MAPPER, applicationNo);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据用户ID查询申请列表
     */
    public List<WaiverApplication> findByUserId(String userId) {
        String sql = "SELECT * FROM waiver_applications WHERE user_id = ? " +
                "ORDER BY submitted_at DESC";
        return jdbcTemplate.query(sql, WAIVER_ROW_MAPPER, userId);
    }

    /**
     * WaiverApplication RowMapper
     */
    private static class WaiverRowMapper implements RowMapper<WaiverApplication> {
        @Override
        public WaiverApplication mapRow(ResultSet rs, int rowNum) throws SQLException {
            return WaiverApplication.builder()
                    .id(rs.getLong("id"))
                    .applicationNo(rs.getString("application_no"))
                    .userId(rs.getString("user_id"))
                    .loanAccount(rs.getString("loan_account"))
                    .waiverAmount(rs.getBigDecimal("waiver_amount"))
                    .reason(rs.getString("reason"))
                    .status(rs.getString("status"))
                    .submittedAt(rs.getTimestamp("submitted_at") != null ?
                            rs.getTimestamp("submitted_at").toLocalDateTime() : null)
                    .reviewedAt(rs.getTimestamp("reviewed_at") != null ?
                            rs.getTimestamp("reviewed_at").toLocalDateTime() : null)
                    .reviewer(rs.getString("reviewer"))
                    .reviewComment(rs.getString("review_comment"))
                    .build();
        }
    }
}
