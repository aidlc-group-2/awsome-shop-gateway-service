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

        // 安全：无条件剥离客户端自带的身份头，杜绝外部伪造 X-User-Id/X-User-Role 透传到下游。
        // 对所有路径（含公共/免认证）都剥离，下游收到的该头只可能由本网关注入。
        ServerWebExchange sanitized = stripInboundIdentityHeaders(exchange);

        // Skip auth for public and docs paths
        if (isPublicPath(path)) {
            return chain.filter(sanitized);
        }

        // Check route metadata for auth-required flag
        if (!isAuthRequired(sanitized)) {
            return chain.filter(sanitized);
        }

        // Extract token
        String authHeader = sanitized.getRequest().getHeaders().getFirst(RouteConstants.HEADER_AUTHORIZATION);
        TokenInfo tokenInfo = TokenInfo.fromAuthorizationHeader(authHeader);
        if (tokenInfo == null) {
            return Mono.error(new AuthenticationException(GatewayErrorCode.AUTH_TOKEN_MISSING));
        }

        String requestId = sanitized.getAttribute(RouteConstants.ATTR_REQUEST_ID);

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
                        sanitized.getAttributes().put(RouteConstants.ATTR_USER_ID, userId);
                    }
                    if (role != null) {
                        sanitized.getAttributes().put(RouteConstants.ATTR_USER_ROLE, role);
                    }

                    // Inject identity headers to downstream request (design FR-G2)。
                    // 用 set 语义覆盖（剥离后此处为新增），避免与外部值并存。
                    ServerHttpRequest mutatedRequest = sanitized.getRequest().mutate()
                            .headers(headers -> {
                                headers.remove(RouteConstants.HEADER_USER_ID);
                                headers.remove(RouteConstants.HEADER_USER_ROLE);
                                if (userId != null) {
                                    headers.set(RouteConstants.HEADER_USER_ID, userId);
                                }
                                if (role != null) {
                                    headers.set(RouteConstants.HEADER_USER_ROLE, role);
                                }
                            })
                            .build();

                    return chain.filter(sanitized.mutate().request(mutatedRequest).build());
                });
    }

    /**
     * 剥离请求中客户端自带的身份头，返回净化后的 exchange。
     */
    private ServerWebExchange stripInboundIdentityHeaders(ServerWebExchange exchange) {
        boolean hasForged = exchange.getRequest().getHeaders().containsKey(RouteConstants.HEADER_USER_ID)
                || exchange.getRequest().getHeaders().containsKey(RouteConstants.HEADER_USER_ROLE);
        if (!hasForged) {
            return exchange;
        }
        log.warn("Stripping client-supplied identity headers from inbound request path={}",
                exchange.getRequest().getURI().getPath());
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(RouteConstants.HEADER_USER_ID);
                    headers.remove(RouteConstants.HEADER_USER_ROLE);
                })
                .build();
        return exchange.mutate().request(stripped).build();
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
