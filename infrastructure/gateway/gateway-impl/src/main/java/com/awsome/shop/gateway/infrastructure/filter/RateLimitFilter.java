package com.awsome.shop.gateway.infrastructure.filter;

import com.awsome.shop.gateway.common.enums.GatewayErrorCode;
import com.awsome.shop.gateway.common.exception.RateLimitException;
import com.awsome.shop.gateway.infrastructure.config.GatewaySecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global filter providing a simple in-memory token-bucket rate limiter (FR-G5).
 *
 * <p>Order: HIGHEST_PRECEDENCE + 10 - runs right after {@code AccessLogFilter}
 * so a request id is available for logging, and before authentication so abusive
 * traffic is rejected cheaply.</p>
 *
 * <p>MVP scope: per client-IP token bucket held in process memory (per gateway
 * instance). Adequate for single-instance / MVP. For a clustered deployment a
 * shared store (e.g. Redis {@code RequestRateLimiter}) should replace this.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    private final GatewaySecurityProperties securityProperties;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewaySecurityProperties.RateLimit cfg = securityProperties.getRateLimit();
        if (cfg == null || !cfg.isEnabled()) {
            return chain.filter(exchange);
        }

        String clientIp = extractClientIp(exchange);
        Bucket bucket = buckets.computeIfAbsent(clientIp, k -> new Bucket(cfg.getCapacity()));

        double refillPerSecond = cfg.getRefillPeriodSeconds() <= 0
                ? cfg.getRefillTokens()
                : (double) cfg.getRefillTokens() / cfg.getRefillPeriodSeconds();

        if (!bucket.tryConsume(cfg.getCapacity(), refillPerSecond)) {
            log.warn("Rate limit exceeded for client {}", clientIp);
            return Mono.error(new RateLimitException(GatewayErrorCode.RATE_LIMIT_EXCEEDED));
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private String extractClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String xff = request.getHeaders().getFirst(HEADER_X_FORWARDED_FOR);
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeaders().getFirst(HEADER_X_REAL_IP);
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        return Optional.ofNullable(request.getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(InetAddress::getHostAddress)
                .orElse("unknown");
    }

    /**
     * Simple token bucket. Thread-safe via per-bucket synchronization.
     */
    private static final class Bucket {
        private double tokens;
        private long lastRefillNanos;

        Bucket(int initialTokens) {
            this.tokens = initialTokens;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume(int capacity, double refillPerSecond) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
                lastRefillNanos = now;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
