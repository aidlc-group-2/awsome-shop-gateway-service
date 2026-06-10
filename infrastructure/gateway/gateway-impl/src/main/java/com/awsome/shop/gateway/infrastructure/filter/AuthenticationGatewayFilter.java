package com.awsome.shop.gateway.infrastructure.filter;

import com.awsome.shop.gateway.common.constants.RouteConstants;
import com.awsome.shop.gateway.common.enums.GatewayErrorCode;
import com.awsome.shop.gateway.common.exception.AuthenticationException;
import com.awsome.shop.gateway.domain.auth.model.AuthenticationResult;
import com.awsome.shop.gateway.domain.auth.model.TokenInfo;
import com.awsome.shop.gateway.domain.auth.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Global filter for JWT token authentication.
 *
 * <p>Order: +100 - executes after AccessLogFilter.</p>
 *
 * <p>Routes can opt out of authentication by setting route metadata
 * {@code auth-required: false} or by matching public path prefixes.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private final AuthenticationService authenticationService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Skip auth for public and docs paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Check route metadata for auth-required flag
        if (!isAuthRequired(exchange)) {
            return chain.filter(exchange);
        }

        // Extract token
        String authHeader = exchange.getRequest().getHeaders().getFirst(RouteConstants.HEADER_AUTHORIZATION);
        TokenInfo tokenInfo = TokenInfo.fromAuthorizationHeader(authHeader);
        if (tokenInfo == null) {
            return Mono.error(new AuthenticationException(GatewayErrorCode.AUTH_TOKEN_MISSING));
        }

        String requestId = exchange.getAttribute(RouteConstants.ATTR_REQUEST_ID);

        // Validate token via auth service
        return authenticationService.validate(tokenInfo.getToken())
                .flatMap(result -> {
                    if (!result.isAuthenticated()) {
                        log.warn("[{}] Authentication failed: {}", requestId, result.getMessage());
                        return Mono.error(new AuthenticationException(
                                GatewayErrorCode.AUTH_TOKEN_INVALID, result.getMessage()));
                    }

                    String userId = result.getUserId() == null ? null : String.valueOf(result.getUserId());
                    String role = result.getRole();

                    log.debug("[{}] Authenticated userId: {}, role: {}", requestId, userId, role);

                    // Store identity for downstream filters (RoleAuthorizationFilter, body injection)
                    if (userId != null) {
                        exchange.getAttributes().put(RouteConstants.ATTR_USER_ID, userId);
                    }
                    if (role != null) {
                        exchange.getAttributes().put(RouteConstants.ATTR_USER_ROLE, role);
                    }

                    // Inject identity headers to downstream request (design FR-G2)
                    ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
                    if (userId != null) {
                        builder.header(RouteConstants.HEADER_USER_ID, userId);
                    }
                    if (role != null) {
                        builder.header(RouteConstants.HEADER_USER_ROLE, role);
                    }
                    ServerHttpRequest mutatedRequest = builder.build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    @Override
    public int getOrder() {
        return 100;
    }

    private boolean isPublicPath(String path) {
        return path.startsWith(RouteConstants.PATH_PREFIX_PUBLIC)
                || path.startsWith(RouteConstants.PATH_PREFIX_DOCS)
                || path.startsWith("/swagger-ui")
                || path.startsWith("/actuator");
    }

    private boolean isAuthRequired(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return true;
        }
        Map<String, Object> metadata = route.getMetadata();
        Object authRequired = metadata.get(RouteConstants.METADATA_AUTH_REQUIRED);
        if (authRequired instanceof Boolean) {
            return (Boolean) authRequired;
        }
        if (authRequired instanceof String) {
            return Boolean.parseBoolean((String) authRequired);
        }
        return true;
    }
}
