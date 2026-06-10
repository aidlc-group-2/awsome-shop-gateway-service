package com.awsome.shop.gateway.infrastructure.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that adds standard security response headers (FR-G5).
 *
 * <p>Order: HIGHEST_PRECEDENCE + 20 - runs early. Headers are registered via
 * {@code beforeCommit} so they are applied right before the response is written
 * and are not lost when the proxied response is assembled.</p>
 *
 * <p>Mitigations: clickjacking (X-Frame-Options), MIME sniffing
 * (X-Content-Type-Options), reflected XSS / content injection (CSP,
 * Referrer-Policy). SQL injection is handled at the service layer via
 * parameterized queries; the gateway contributes defense-in-depth headers.</p>
 */
@Slf4j
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("X-XSS-Protection", "1; mode=block");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'");
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
