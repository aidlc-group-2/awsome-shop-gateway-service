package com.awsome.shop.gateway.infrastructure.auth.client;

import com.awsome.shop.gateway.application.auth.dto.AuthValidateRequest;
import com.awsome.shop.gateway.application.auth.dto.AuthValidateResponse;
import com.awsome.shop.gateway.common.enums.GatewayErrorCode;
import com.awsome.shop.gateway.common.exception.AuthenticationException;
import com.awsome.shop.gateway.domain.auth.model.AuthenticationResult;
import com.awsome.shop.gateway.domain.auth.service.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Auth service client that validates tokens via external auth service using WebClient.
 */
@Slf4j
@Component
public class AuthServiceClient implements AuthenticationService {

    private final WebClient webClient;
    private final String authValidateUrl;
    private final Duration timeout;

    public AuthServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${gateway.auth.validate-url}") String authValidateUrl,
            @Value("${gateway.auth.timeout:5s}") Duration timeout) {
        this.webClient = webClientBuilder.build();
        this.authValidateUrl = authValidateUrl;
        this.timeout = timeout;
    }

    @Override
    public Mono<AuthenticationResult> validate(String token) {
        AuthValidateRequest request = AuthValidateRequest.builder()
                .token(token)
                .build();

        return webClient.post()
                .uri(authValidateUrl)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AuthValidateResponse.class)
                .timeout(timeout)
                .map(response -> {
                    if (response.isSuccess()) {
                        return AuthenticationResult.success(response.getUserId(), response.getRole());
                    }
                    return AuthenticationResult.failure(response.getMessage());
                })
                .onErrorResume(ex -> {
                    if (ex instanceof AuthenticationException) {
                        return Mono.error(ex);
                    }
                    log.error("Auth service call failed: {}", ex.getMessage(), ex);
                    return Mono.error(new AuthenticationException(
                            GatewayErrorCode.AUTH_SERVICE_UNAVAILABLE,
                            "Authentication service unavailable: " + ex.getMessage()));
                });
    }
}
