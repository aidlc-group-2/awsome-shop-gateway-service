package com.awsome.shop.gateway.domain.auth.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TokenInfo 单元测试
 */
class TokenInfoTest {

    @Test
    @DisplayName("正常 Bearer 头应提取出 token")
    void shouldExtractTokenFromBearerHeader() {
        TokenInfo info = TokenInfo.fromAuthorizationHeader("Bearer abc.def.ghi");

        assertThat(info).isNotNull();
        assertThat(info.getToken()).isEqualTo("abc.def.ghi");
    }

    @Test
    @DisplayName("token 两侧空白应被去除")
    void shouldTrimToken() {
        TokenInfo info = TokenInfo.fromAuthorizationHeader("Bearer   abc.def  ");

        assertThat(info).isNotNull();
        assertThat(info.getToken()).isEqualTo("abc.def");
    }

    @Test
    @DisplayName("无 Bearer 前缀应返回 null")
    void shouldReturnNullWithoutBearerPrefix() {
        assertThat(TokenInfo.fromAuthorizationHeader("Basic dXNlcjpwd2Q=")).isNull();
        assertThat(TokenInfo.fromAuthorizationHeader("abc.def.ghi")).isNull();
    }

    @Test
    @DisplayName("null 或仅前缀的头应返回 null")
    void shouldReturnNullForNullOrEmptyToken() {
        assertThat(TokenInfo.fromAuthorizationHeader(null)).isNull();
        assertThat(TokenInfo.fromAuthorizationHeader("Bearer ")).isNull();
        assertThat(TokenInfo.fromAuthorizationHeader("Bearer    ")).isNull();
    }
}
