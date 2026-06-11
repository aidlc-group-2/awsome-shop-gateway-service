package com.awsome.shop.gateway.infrastructure.filter;

import com.awsome.shop.gateway.common.constants.RouteConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AccessLogFilter 单元测试
 */
class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter();

    /**
     * 捕获放行后 exchange 的 filter chain stub
     */
    private static class CapturingChain implements GatewayFilterChain {
        ServerWebExchange captured;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.captured = exchange;
            return Mono.empty();
        }
    }

    @Test
    @DisplayName("filter 应生成 requestId 并注入 attribute 和请求头")
    void filterShouldGenerateRequestIdAndInject() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").build());
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String requestId = exchange.getAttribute(RouteConstants.ATTR_REQUEST_ID);
        assertThat(requestId).isNotNull().hasSize(16);
        assertThat(exchange.<Long>getAttribute(RouteConstants.ATTR_REQUEST_START_TIME)).isNotNull();
        assertThat(chain.captured.getRequest().getHeaders().getFirst(RouteConstants.HEADER_REQUEST_ID))
                .isEqualTo(requestId);
    }

    @Test
    @DisplayName("两次请求应生成不同的 requestId")
    void requestIdsShouldBeUnique() {
        MockServerWebExchange first = MockServerWebExchange.from(
                MockServerHttpRequest.get("/a").build());
        MockServerWebExchange second = MockServerWebExchange.from(
                MockServerHttpRequest.get("/b").build());
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(first, chain)).verifyComplete();
        StepVerifier.create(filter.filter(second, chain)).verifyComplete();

        assertThat(first.<String>getAttribute(RouteConstants.ATTR_REQUEST_ID))
                .isNotEqualTo(second.<String>getAttribute(RouteConstants.ATTR_REQUEST_ID));
    }

    @Test
    @DisplayName("X-Forwarded-For 存在时取第一个 IP（通过正常放行验证不抛错）")
    void filterShouldHandleForwardedHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2")
                        .header("X-Real-IP", "10.0.0.3")
                        .remoteAddress(new InetSocketAddress("192.168.1.1", 12345))
                        .build());
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured).isNotNull();
    }

    @Test
    @DisplayName("无任何 IP 信息时也应正常放行")
    void filterShouldHandleMissingClientIp() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").build());
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured).isNotNull();
    }

    @Test
    @DisplayName("过滤器应为最高优先级")
    void orderShouldBeHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
