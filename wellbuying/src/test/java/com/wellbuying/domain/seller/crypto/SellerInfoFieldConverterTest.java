package com.wellbuying.domain.seller.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SellerInfoFieldConverterTest {

    // application.yaml의 로컬 기본값과 동일한 32바이트 AES 키(Base64)
    private final SellerInfoFieldConverter converter =
            new SellerInfoFieldConverter(new SellerInfoEncryptionProperties("Q0hBTkdFX01FX2xvY2FsX2Rldl8zMl9ieXRlX2tleSE="));

    @Test
    void 암호화한_값을_복호화하면_원본과_같다() {
        String plaintext = "110-123-456789";

        String encrypted = converter.convertToDatabaseColumn(plaintext);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void 암호화된_값은_평문과_다르다() {
        String plaintext = "110-123-456789";

        String encrypted = converter.convertToDatabaseColumn(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
    }

    @Test
    void 같은_평문도_매번_다른_암호문을_생성한다() {
        String plaintext = "홍길동";

        String encryptedFirst = converter.convertToDatabaseColumn(plaintext);
        String encryptedSecond = converter.convertToDatabaseColumn(plaintext);

        assertThat(encryptedFirst).isNotEqualTo(encryptedSecond);
    }

    @Test
    void null을_암호화하면_null을_반환한다() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void null을_복호화하면_null을_반환한다() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void 빈_문자열을_복호화하면_예외가_발생한다() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute(""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void IV_길이보다_짧은_데이터를_복호화하면_예외가_발생한다() {
        String tooShort = java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tooShort))
                .isInstanceOf(IllegalStateException.class);
    }
}
