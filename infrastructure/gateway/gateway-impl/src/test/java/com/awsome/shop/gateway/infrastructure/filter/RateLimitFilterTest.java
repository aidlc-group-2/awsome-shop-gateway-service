package com.awsome.shop.gateway.infrastructure.filter;

import com.awsome.shop.gateway.common.exception.RateLimitException;
import com.awsome.shop.gateway.infrastructure.config.GatewaySecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RateLimitFilter 单元测试
 */
class RateLimitFilterTest {

    private GatewaySecurityProperties properties(boolean enabled, int capacity) {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        GatewaySecurityProperties.RateLimit rateLimit = new GatewaySecurityProperties.RateLimit();
        rateLimit.setEnabled(enabled);
        rateLimit.setCapacity(capacity);
        rateLimit.setRefillTokens(capacity);
        rateLimit.setRefillPeriodSeconds(3600); // 测试期间几乎不补充令牌
        properties.setRateLimit(rateLimit);
        return properties;
    }

    private GatewayFilterChain countingChain(AtomicInteger counter) {
        return (ServerWebExchange exchange) -> {
            counter.incrementAndGet();
            return Mono.empty();
        };
    }

    private MockServerWebExchange exchangeFromIp(String ip) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header("X-Forwarded-For", ip)
                        .build());
    }

    @Test
    @DisplayName("限流关闭时应直接放行")
    void disabledRateLimitShouldPassThrough() {
        RateLimitFilter filter = new RateLimitFilter(properties(false, 1));
        AtomicInteger passed = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            StepVerifier.create(filter.filter(exchangeFromIp("1.1.1.1"), countingChain(passed)))
                    .verifyComplete();
        }

        assertThat(passed.get()).isEqualTo(5);
    }

    @Test
    @DisplayName("未超限的请求应放行")
    void requestsWithinCapacityShouldPass() {
        RateLimitFilter filter = new RateLimitFilter(properties(true, 3));
        AtomicInteger passed = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            StepVerifier.create(filter.filter(exchangeFromIp("2.2.2.2"), countingChain(passed)))
                    .verifyComplete();
        }

        assertThat(passed.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("超过容量的请求应抛 RateLimitException")
    void requestsBeyondCapacityShouldBeRejected() {
        RateLimitFilter filter = new RateLimitFilter(properties(true, 2));
        AtomicInteger passed = new AtomicInteger();

        StepVerifier.create(filter.filter(exchangeFromIp("3.3.3.3"), countingChain(passed))).verifyComplete();
        StepVerifier.create(filter.filter(exchangeFromIp("3.3.3.3"), countingChain(passed))).verifyComplete();
        StepVerifier.create(filter.filter(exchangeFromIp("3.3.3.3"), countingChain(passed)))
                .expectError(RateLimitException.class)
                .verify();

        assertThat(passed.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("不同客户端 IP 使用独立令牌桶")
    void differentClientsShouldHaveSeparateBuckets() {
        RateLimitFilter filter = new RateLimitFilter(properties(true, 1));
        AtomicInteger passed = new AtomicInteger();

        StepVerifier.create(filter.filter(exchangeFromIp("4.4.4.4"), countingChain(passed))).verifyComplete();
        // 4.4.4.4 已耗尽，5.5.5.5 不受影响
        StepVerifier.create(filter.filter(exchangeFromIp("4.4.4.4"), countingChain(passed)))
                .expectError(RateLimitException.class)
                .verify();
        StepVerifier.create(filter.filter(exchangeFromIp("5.5.5.5"), countingChain(passed))).verifyComplete();

        assertThat(passed.get()).isEqualTo(2);
    }
}
