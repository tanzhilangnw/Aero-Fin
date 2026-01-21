package com.aerofin.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全配置属性
 * <p>
 * 对应 application.yml 中的 aero-fin.security 配置
 * <p>
 * 示例：
 * ```yaml
 * aero-fin:
 *   security:
 *     enabled: true
 *     api-keys:
 *       - sk-aerofin-prod-2024-abc123def456
 *       - sk-aerofin-test-2024-xyz789uvw012
 * ```
 *
 * @author Aero-Fin Team
 */
@Data
@Component
@ConfigurationProperties(prefix = "aero-fin.security")
public class SecurityProperties {

    /**
     * 是否启用API认证
     */
    private Boolean enabled = true;

    /**
     * 有效的API Key列表
     */
    private List<String> apiKeys = new ArrayList<>();

    /**
     * JWT配置
     */
    private JwtProperties jwt = new JwtProperties();

    /**
     * JWT配置属性
     */
    @Data
    public static class JwtProperties {
        /**
         * JWT密钥（生产环境应该从环境变量读取）
         */
        private String secret = "change-me-in-production";

        /**
         * Token过期时间（毫秒）
         */
        private Long expiration = 3600000L; // 1小时

        /**
         * Token刷新时间（毫秒）
         */
        private Long refreshExpiration = 604800000L; // 7天
    }
}
