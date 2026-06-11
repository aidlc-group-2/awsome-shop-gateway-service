package com.awsome.shop.gateway.infrastructure.filter;

import com.awsome.shop.gateway.common.constants.RouteConstants;
import com.awsome.shop.gateway.common.exception.AuthenticationException;
import com.awsome.shop.gateway.domain.auth.model.AuthenticationResult;
import com.awsome.shop.gateway.domain.auth.service.AuthenticationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * AuthenticationGatewayFilter 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationGatewayFilterTest {

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private AuthenticationGatewayFilter filter;

    /**
     * 捕获放行后 exchange 的 filter chain stub
     */
    private static class CapturingChain implements GatewayFilterChain {
        ServerWebExchange captured;
        boolean called;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.captured = exchange;
            this.called = true;
            return Mono.empty();
        }
    }

    @Test
    @DisplayName("公共路径应直接放行，不调用认证服务")
    void publicPathShouldBypassAuthentication() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/public/login").build());
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.called).isTrue();
        verify(authenticationService, never()).validate(anyString());
    }

    @Test
    @DisplayName("文档与监控路径应直接放行")
    void docsAndActuatorPathsShouldBypassAuthentication() {
        for (String path : new String[]{"/v3/api-docs/gateway", "/swagger-ui/index.html", "/actuator/health"}) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get(path).build());
            CapturingChain chain = new CapturingChain();

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(chain.called).as("path %s should bypass", path).isTrue();
        }
        verify(authenticationService, never()).validate(anyString());
    }

    @Test
    @DisplayName("缺少 token 应返回 AuthenticationException")
    void missingTokenShouldFail() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").build());
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(AuthenticationException.class)
                .verify();

        assertThat(chain.called).isFalse();
    }

    @Test
    @DisplayName("token 有效时放行并注入身份 header 与 attribute")
    void validTokenShouldInjectIdentity() {
        when(authenticationService.validate("good-token"))
                .thenReturn(Mono.just(AuthenticationResult.success(42L, "ADMIN")));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header(RouteConstants.HEADER_AUTHORIZATION, "Bearer good-token")
                        .build());
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.called).isTrue();
        assertThat(chain.captured.getRequest().getHeaders().getFirst(RouteConstants.HEADER_USER_ID))
                .isEqualTo("42");
        assertThat(chain.captured.getRequest().getHeaders().getFirst(RouteConstants.HEADER_USER_ROLE))
                .isEqualTo("ADMIN");
        assertThat(chain.captured.getAttributes())
                .containsEntry(RouteConstants.ATTR_USER_ID, "42")
                .containsEntry(RouteConstants.ATTR_USER_ROLE, "ADMIN");
    }

    @Test
    @DisplayName("token 无效时返回 AuthenticationException")
    void invalidTokenShouldFail() {
        when(authenticationService.validate("bad-token"))
                .thenReturn(Mono.just(AuthenticationResult.failure("expired")));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header(RouteConstants.HEADER_AUTHORIZATION, "Bearer bad-token")
                        .build());
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(AuthenticationException.class)
                .verify();

        assertThat(chain.called).isFalse();
    }

    @Test
    @DisplayName("过滤器顺序应为 +100")
    void orderShouldBe100() {
        assertThat(filter.getOrder()).isEqualTo(100);
    }
}
