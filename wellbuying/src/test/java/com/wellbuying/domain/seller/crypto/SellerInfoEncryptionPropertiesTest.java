package com.wellbuying.domain.seller.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SellerInfoEncryptionPropertiesTest {

    @Test
    void 정상적인_32바이트_Base64_키는_생성된다() {
        SellerInfoEncryptionProperties properties =
                new SellerInfoEncryptionProperties("Q0hBTkdFX01FX2xvY2FsX2Rldl8zMl9ieXRlX2tleSE=");

        assertThat(properties.decodedKeyBytes()).hasSize(32);
    }

    @Test
    void 키가_비어있으면_생성_시점에_예외가_발생한다() {
        assertThatThrownBy(() -> new SellerInfoEncryptionProperties(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SellerInfoEncryptionProperties(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SellerInfoEncryptionProperties("   "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 키가_Base64_형식이_아니면_생성_시점에_예외가_발생한다() {
        assertThatThrownBy(() -> new SellerInfoEncryptionProperties("이건 Base64가 아님!!"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 키_길이가_32바이트가_아니면_생성_시점에_예외가_발생한다() {
        String tooShortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new SellerInfoEncryptionProperties(tooShortKey))
                .isInstanceOf(IllegalStateException.class);
    }
}
