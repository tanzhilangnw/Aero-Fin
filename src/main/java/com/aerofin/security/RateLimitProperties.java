package com.aerofin.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 限流配置属性
 * <p>
 * 对应 application.yml 中的 aero-fin.rate-limit 配置
 * <p>
 * 示例：
 * ```yaml
 * aero-fin:
 *   rate-limit:
 *     enabled: true
 *     requests-per-minute: 60
 *     burst-capacity: 10
 * ```
 *
 * @author Aero-Fin Team
 */
@Data
@Component
@ConfigurationProperties(prefix = "aero-fin.rate-limit")
public class RateLimitProperties {

    /**
     * 是否启用限流
     */
    private Boolean enabled = true;

    /**
     * 每分钟允许的请求数
     */
    private Integer requestsPerMinute = 60;

    /**
     * 突发容量（允许超过limit的请求数）
     */
    private Integer burstCapacity = 10;
}
