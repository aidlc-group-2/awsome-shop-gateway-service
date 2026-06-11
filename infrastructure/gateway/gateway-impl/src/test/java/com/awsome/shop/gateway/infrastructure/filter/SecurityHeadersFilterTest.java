package com.awsome.shop.gateway.infrastructure.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SecurityHeadersFilter 单元测试
 */
class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    @DisplayName("响应提交时应附加全部安全头")
    void shouldAddSecurityHeadersOnCommit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").build());
        // chain 中触发响应提交，使 beforeCommit 回调执行
        GatewayFilterChain chain = (ServerWebExchange ex) -> ex.getResponse().setComplete();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("X-XSS-Protection")).isEqualTo("1; mode=block");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(headers.getFirst("Content-Security-Policy"))
                .isEqualTo("default-src 'self'; frame-ancestors 'none'");
    }

    @Test
    @DisplayName("过滤器应为最高优先级 +20")
    void orderShouldBeHighestPrecedencePlus20() {
        assertThat(filter.getOrder()).isEqualTo(Integer.MIN_VALUE + 20);
    }
}
