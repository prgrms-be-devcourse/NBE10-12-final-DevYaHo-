package com.wellbuying.domain.payment.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnvKeyAesGcmEncryptorTest {

    private static final String BILLING_KEY = "bk_test_ZORzdMaqN3wQd5k6ygr5K";

    private static String randomMasterKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    @DisplayName("암호화한 빌링키를 다시 복호화하면 원문이 나온다")
    void 암호화_복호화_왕복() {
        EnvKeyAesGcmEncryptor encryptor = new EnvKeyAesGcmEncryptor(randomMasterKey());

        String encrypted = encryptor.encrypt(BILLING_KEY);

        assertThat(encrypted).isNotEqualTo(BILLING_KEY);
        assertThat(encryptor.decrypt(encrypted)).isEqualTo(BILLING_KEY);
    }

    @Test
    @DisplayName("같은 값을 두 번 암호화해도 IV가 매번 달라 암호문이 달라진다")
    void 매번_다른_암호문() {
        EnvKeyAesGcmEncryptor encryptor = new EnvKeyAesGcmEncryptor(randomMasterKey());

        String first = encryptor.encrypt(BILLING_KEY);
        String second = encryptor.encrypt(BILLING_KEY);

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptor.decrypt(first)).isEqualTo(encryptor.decrypt(second));
    }

    @Test
    @DisplayName("암호문이 변조되면 복호화가 실패한다 - GCM 인증 태그가 검증을 막는다")
    void 변조된_암호문은_거부된다() {
        EnvKeyAesGcmEncryptor encryptor = new EnvKeyAesGcmEncryptor(randomMasterKey());
        byte[] raw = Base64.getDecoder().decode(encryptor.encrypt(BILLING_KEY));
        raw[raw.length - 1] ^= 0x01;

        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("빌링키 복호화 실패");
    }

    @Test
    @DisplayName("다른 마스터 키로는 복호화할 수 없다")
    void 다른_키로는_복호화_불가() {
        String encrypted = new EnvKeyAesGcmEncryptor(randomMasterKey()).encrypt(BILLING_KEY);
        EnvKeyAesGcmEncryptor other = new EnvKeyAesGcmEncryptor(randomMasterKey());

        assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("마스터 키가 비어 있으면 생성 시점에 실패한다 - 결제 도중이 아니라 부팅에서 끊기 위함")
    void 키가_없으면_생성_실패() {
        assertThatThrownBy(() -> new EnvKeyAesGcmEncryptor(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BILLING_KEY_MASTER_KEY");
    }

    @Test
    @DisplayName("마스터 키 길이가 32바이트가 아니면 생성 시점에 실패한다")
    void 키_길이가_틀리면_생성_실패() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new EnvKeyAesGcmEncryptor(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }
}
