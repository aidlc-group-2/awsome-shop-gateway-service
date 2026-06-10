package com.awsome.shop.gateway.application.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Auth validation response DTO from the external auth service.
 *
 * <p>Contract aligned with Unit2 auth-service
 * {@code POST /api/v1/internal/auth/validate}, which returns
 * {@code {success, userId, role, message}} (FR-A7).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthValidateResponse {

    /** Whether the token is valid. */
    private boolean success;

    /** Authenticated user id. */
    private Long userId;

    /** User role (EMPLOYEE / ADMIN). */
    private String role;

    /** Optional message (failure reason). */
    private String message;
}
