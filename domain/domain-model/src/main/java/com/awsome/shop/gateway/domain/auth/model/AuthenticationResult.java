package com.awsome.shop.gateway.domain.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Authentication result value object.
 *
 * <p>Carries the authenticated identity ({@code userId}) and {@code role}
 * so downstream filters can inject {@code X-User-Id}/{@code X-User-Role}
 * and enforce role-based authorization.</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationResult {

    private boolean authenticated;

    private Long userId;

    private String role;

    private String message;

    public static AuthenticationResult success(Long userId, String role) {
        return AuthenticationResult.builder()
                .authenticated(true)
                .userId(userId)
                .role(role)
                .build();
    }

    public static AuthenticationResult failure(String message) {
        return AuthenticationResult.builder()
                .authenticated(false)
                .message(message)
                .build();
    }
}
