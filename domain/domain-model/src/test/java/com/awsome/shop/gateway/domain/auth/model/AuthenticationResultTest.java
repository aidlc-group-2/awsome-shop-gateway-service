package com.awsome.shop.gateway.domain.auth.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuthenticationResult 单元测试
 */
class AuthenticationResultTest {

    @Test
    @DisplayName("success 工厂方法应携带 userId 和 role")
    void successShouldCarryIdentity() {
        AuthenticationResult result = AuthenticationResult.success(42L, "ADMIN");

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getUserId()).isEqualTo(42L);
        assertThat(result.getRole()).isEqualTo("ADMIN");
        assertThat(result.getMessage()).isNull();
    }

    @Test
    @DisplayName("failure 工厂方法应仅携带消息")
    void failureShouldCarryMessageOnly() {
        AuthenticationResult result = AuthenticationResult.failure("token expired");

        assertThat(result.isAuthenticated()).isFalse();
        assertThat(result.getUserId()).isNull();
        assertThat(result.getRole()).isNull();
        assertThat(result.getMessage()).isEqualTo("token expired");
    }
}
