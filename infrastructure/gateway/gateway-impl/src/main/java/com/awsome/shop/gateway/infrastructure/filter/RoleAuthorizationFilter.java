package com.awsome.shop.gateway.infrastructure.filter;

import com.awsome.shop.gateway.common.constants.RouteConstants;
import com.awsome.shop.gateway.common.enums.GatewayErrorCode;
import com.awsome.shop.gateway.common.exception.AuthorizationException;
import com.awsome.shop.gateway.infrastructure.config.GatewaySecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global filter enforcing role-based access control on admin paths (FR-G3).
 *
 * <p>Order: +150 - runs after {@code AuthenticationGatewayFilter} (+100), so the
 * authenticated role is already available in the exchange attributes, and before
 * {@code OperatorIdInjectionFilter} (+200).</p>
 *
 * <p>If the request path matches any configured {@code gateway.security.admin-paths}
 * pattern, the authenticated user must have role {@code ADMIN}; otherwise a 403 is
 * returned. Non-admin paths pass through unchanged.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleAuthorizationFilter implements GlobalFilter, Ordered {

    private final GatewaySecurityProperties securityProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (!isAdminPath(path)) {
            return chain.filter(exchange);
        }

        String role = exchange.getAttribute(RouteConstants.ATTR_USER_ROLE);
        String requestId = exchange.getAttribute(RouteConstants.ATTR_REQUEST_ID);

        if (!RouteConstants.ROLE_ADMIN.equals(role)) {
            log.warn("[{}] Forbidden: admin path {} requires ADMIN, actual role: {}", requestId, path, role);
            return Mono.error(new AuthorizationException(GatewayErrorCode.AUTH_FORBIDDEN));
        }

        return chain.filter(exchange);
    }

    private boolean isAdminPath(String path) {
        List<String> patterns = securityProperties.getAdminPaths();
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 150;
    }
}
