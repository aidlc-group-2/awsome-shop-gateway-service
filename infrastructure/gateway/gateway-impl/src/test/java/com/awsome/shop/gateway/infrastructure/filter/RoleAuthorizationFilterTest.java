package com.awsome.shop.gateway.infrastructure.filter;

import com.awsome.shop.gateway.common.constants.RouteConstants;
import com.awsome.shop.gateway.common.exception.AuthorizationException;
import com.awsome.shop.gateway.infrastructure.config.GatewaySecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RoleAuthorizationFilter 单元测试
 */
class RoleAuthorizationFilterTest {

    private RoleAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setAdminPaths(List.of("/api/v1/admin/**"));
        filter = new RoleAuthorizationFilter(properties);
    }

    private GatewayFilterChain chain(AtomicBoolean called) {
        return (ServerWebExchange exchange) -> {
            called.set(true);
            return Mono.empty();
        };
    }

    @Test
    @DisplayName("非 admin 路径应直接放行")
    void nonAdminPathShouldPass() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/products").build());
        AtomicBoolean called = new AtomicBoolean(false);

        StepVerifier.create(filter.filter(exchange, chain(called))).verifyComplete();

        assertThat(called).isTrue();
    }

    @Test
    @DisplayName("admin 路径 + ADMIN 角色应放行")
    void adminPathWithAdminRoleShouldPass() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/users").build());
        exchange.getAttributes().put(RouteConstants.ATTR_USER_ROLE, "ADMIN");
        AtomicBoolean called = new AtomicBoolean(false);

        StepVerifier.create(filter.filter(exchange, chain(called))).verifyComplete();

        assertThat(called).isTrue();
    }

    @Test
    @DisplayName("admin 路径 + 非 ADMIN 角色应拒绝")
    void adminPathWithNonAdminRoleShouldBeForbidden() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/users").build());
        exchange.getAttributes().put(RouteConstants.ATTR_USER_ROLE, "EMPLOYEE");
        AtomicBoolean called = new AtomicBoolean(false);

        StepVerifier.create(filter.filter(exchange, chain(called)))
                .expectError(AuthorizationException.class)
                .verify();

        assertThat(called).isFalse();
    }

    @Test
    @DisplayName("admin 路径无角色信息时应拒绝")
    void adminPathWithoutRoleShouldBeForbidden() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/config").build());
        AtomicBoolean called = new AtomicBoolean(false);

        StepVerifier.create(filter.filter(exchange, chain(called)))
                .expectError(AuthorizationException.class)
                .verify();

        assertThat(called).isFalse();
    }

    @Test
    @DisplayName("未配置 adminPaths 时全部放行")
    void emptyAdminPathsShouldPassAll() {
        RoleAuthorizationFilter permissive = new RoleAuthorizationFilter(new GatewaySecurityProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/users").build());
        AtomicBoolean called = new AtomicBoolean(false);

        StepVerifier.create(permissive.filter(exchange, chain(called))).verifyComplete();

        assertThat(called).isTrue();
    }

    @Test
    @DisplayName("过滤器顺序应为 +150")
    void orderShouldBe150() {
        assertThat(filter.getOrder()).isEqualTo(150);
    }
}
