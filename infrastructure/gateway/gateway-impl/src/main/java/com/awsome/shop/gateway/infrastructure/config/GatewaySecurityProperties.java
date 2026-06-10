package com.awsome.shop.gateway.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Gateway security configuration ({@code gateway.security.*}).
 *
 * <p>Backs role-based authorization (FR-G3) and rate limiting (FR-G5).</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    /**
     * Ant-style path patterns that require the ADMIN role.
     * Matched against the request path; if a request matches any pattern,
     * the authenticated user must have role ADMIN.
     */
    private List<String> adminPaths = new ArrayList<>();

    /** Rate limiting settings. */
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class RateLimit {
        /** Whether rate limiting is enabled. */
        private boolean enabled = true;
        /** Maximum burst capacity (tokens) per client. */
        private int capacity = 100;
        /** Tokens refilled per refill period. */
        private int refillTokens = 100;
        /** Refill period in seconds. */
        private int refillPeriodSeconds = 1;
    }
}
