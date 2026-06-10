package com.awsome.shop.gateway.common.exception;

import com.awsome.shop.gateway.common.enums.ErrorCode;

/**
 * Rate limit exception (HTTP 429).
 *
 * <p>Raised when a client exceeds the configured request rate (design FR-G5).
 * Error codes use the {@code RATE_} prefix, which {@code GlobalExceptionHandler}
 * maps to 429 Too Many Requests.</p>
 */
public class RateLimitException extends GatewayException {

    public RateLimitException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RateLimitException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
