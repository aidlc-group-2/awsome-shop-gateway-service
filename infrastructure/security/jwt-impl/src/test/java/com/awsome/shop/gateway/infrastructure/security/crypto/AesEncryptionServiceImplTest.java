package com.awsome.shop.gateway.infrastructure.security.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AesEncryptionServiceImpl 单元测试（AES-256-GCM，不依赖 Spring 上下文）
 */
class AesEncryptionServiceImplTest {

    private static final String KEY = "unit-test-encryption-key-32bytes";

    private AesEncryptionServiceImpl encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new AesEncryptionServiceImpl(KEY);
    }

    @Test
    @DisplayName("encrypt 后 decrypt 应还原明文")
    void encryptThenDecryptShouldRoundTrip() {
        String plaintext = "sensitive-config-value-密码123";

        String ciphertext = encryptionService.encrypt(plaintext);

        assertThat(ciphertext).startsWith("ENC:").isNotEqualTo(plaintext);
        assertThat(encryptionService.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("相同明文两次加密结果不同（随机 IV）")
    void encryptShouldProduceDifferentCiphertexts() {
        String first = encryptionService.encrypt("same-input");
        String second = encryptionService.encrypt("same-input");

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptionService.decrypt(first)).isEqualTo("same-input");
        assertThat(encryptionService.decrypt(second)).isEqualTo("same-input");
    }

    @Test
    @DisplayName("isEncrypted 仅对 ENC: 前缀返回 true")
    void isEncryptedShouldDetectPrefix() {
        String ciphertext = encryptionService.encrypt("data");

        assertThat(encryptionService.isEncrypted(ciphertext)).isTrue();
        assertThat(encryptionService.isEncrypted("plain-text")).isFalse();
        assertThat(encryptionService.isEncrypted("")).isFalse();
        assertThat(encryptionService.isEncrypted(null)).isFalse();
    }

    @Test
    @DisplayName("decrypt 非加密数据时原样返回")
    void decryptShouldReturnPlaintextAsIs() {
        assertThat(encryptionService.decrypt("plain-text")).isEqualTo("plain-text");
        assertThat(encryptionService.decrypt("")).isEmpty();
    }

    @Test
    @DisplayName("null 入参应抛 IllegalArgumentException")
    void nullInputShouldThrow() {
        assertThatThrownBy(() -> encryptionService.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encryptionService.decrypt(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("encrypt 空串应原样返回")
    void encryptEmptyStringShouldReturnAsIs() {
        assertThat(encryptionService.encrypt("")).isEmpty();
    }

    @Test
    @DisplayName("用其他密钥解密应失败")
    void decryptWithDifferentKeyShouldFail() {
        String ciphertext = encryptionService.encrypt("secret");
        AesEncryptionServiceImpl other = new AesEncryptionServiceImpl("another-key-with-32-bytes-length!");

        assertThatThrownBy(() -> other.decrypt(ciphertext))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("短密钥应自动填充到 32 字节并可正常工作")
    void shortKeyShouldBePadded() {
        AesEncryptionServiceImpl shortKeyService = new AesEncryptionServiceImpl("shortkey");

        String ciphertext = shortKeyService.encrypt("data");

        assertThat(shortKeyService.decrypt(ciphertext)).isEqualTo("data");
    }
}
