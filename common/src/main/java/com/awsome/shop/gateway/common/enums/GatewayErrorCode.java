package com.awsome.shop.gateway.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Gateway-specific error codes
 */
@Getter
@AllArgsConstructor
public enum GatewayErrorCode implements ErrorCode {

    AUTH_TOKEN_MISSING("AUTH_001", "Authorization token is missing"),
    AUTH_TOKEN_INVALID("AUTH_002", "Authorization token is invalid"),
    AUTH_TOKEN_EXPIRED("AUTH_003", "Authorization token has expired"),
    AUTH_SERVICE_UNAVAILABLE("AUTH_004", "Authentication service is unavailable"),

    AUTH_FORBIDDEN("AUTHZ_001", "Access denied: administrator role required"),

    RATE_LIMIT_EXCEEDED("RATE_001", "Too many requests, please try again later"),

    GATEWAY_SERVICE_UNAVAILABLE("SYS_001", "Backend service is unavailable"),
    GATEWAY_TIMEOUT("SYS_002", "Backend service timeout"),
    GATEWAY_INTERNAL_ERROR("SYS_003", "Gateway internal error"),
    GATEWAY_BAD_GATEWAY("SYS_004", "Bad gateway response from backend");

    private final String code;
    private final String message;
}
