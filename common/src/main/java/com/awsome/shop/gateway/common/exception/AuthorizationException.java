package com.awsome.shop.gateway.common.exception;

import com.awsome.shop.gateway.common.enums.ErrorCode;

/**
 * Authorization exception (HTTP 403).
 *
 * <p>Raised when an authenticated principal lacks the required role to
 * access a protected resource (design FR-G3). Error codes use the
 * {@code AUTHZ_} prefix, which {@code GlobalExceptionHandler} maps to 403.</p>
 */
public class AuthorizationException extends GatewayException {

    public AuthorizationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthorizationException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
