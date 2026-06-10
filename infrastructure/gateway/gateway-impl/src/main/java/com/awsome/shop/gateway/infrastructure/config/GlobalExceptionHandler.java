package com.awsome.shop.gateway.infrastructure.config;

import com.awsome.shop.gateway.common.constants.RouteConstants;
import com.awsome.shop.gateway.common.dto.ErrorResponse;
import com.awsome.shop.gateway.common.exception.AuthenticationException;
import com.awsome.shop.gateway.common.exception.GatewayException;
import com.awsome.shop.gateway.common.exception.ServiceUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Reactive global exception handler for gateway errors.
 *
 * <p>Ordered at -1 to take priority over Spring Boot's default error handler.</p>
 */
@Slf4j
@Order(-1)
@Component
@RequiredArgsConstructor
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        String requestId = exchange.getAttribute(RouteConstants.ATTR_REQUEST_ID);
        String path = exchange.getRequest().getURI().getPath();

        HttpStatus status;
        String code;
        String message;

        if (ex instanceof AuthenticationException authEx) {
            status = HttpStatus.UNAUTHORIZED;
            code = authEx.getErrorCode();
            message = authEx.getErrorMessage();
        } else if (ex instanceof ServiceUnavailableException svcEx) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = svcEx.getErrorCode();
            message = svcEx.getErrorMessage();
        } else if (ex instanceof GatewayException gwEx) {
            status = resolveStatus(gwEx.getErrorCode());
            code = gwEx.getErrorCode();
            message = gwEx.getErrorMessage();
        } else if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            code = "GATEWAY_" + status.value();
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
        } else if (ex instanceof ConnectException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = "SYS_001";
            message = "Backend service is unavailable";
        } else if (ex instanceof TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            code = "SYS_002";
            message = "Backend service timeout";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = "SYS_003";
            message = "Gateway internal error";
            log.error("[{}] Unhandled exception at {}: {}", requestId, path, ex.getMessage(), ex);
        }

        if (status.is4xxClientError()) {
            log.warn("[{}] {} {} - {} {}", requestId, status.value(), path, code, message);
        } else if (status.is5xxServerError() && !(ex instanceof ConnectException || ex instanceof TimeoutException)) {
            log.error("[{}] {} {} - {} {}", requestId, status.value(), path, code, message);
        }

        ErrorResponse errorResponse = ErrorResponse.of(code, message, requestId, path);

        byte[] responseBytes;
        try {
            responseBytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            responseBytes = ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(responseBytes)));
    }

    private HttpStatus resolveStatus(String errorCode) {
        if (errorCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (errorCode.startsWith("AUTH_")) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (errorCode.startsWith("AUTHZ_")) {
            return HttpStatus.FORBIDDEN;
        }
        if (errorCode.startsWith("RATE_")) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (errorCode.startsWith("PARAM_")) {
            return HttpStatus.BAD_REQUEST;
        }
        if (errorCode.startsWith("NOT_FOUND_")) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
