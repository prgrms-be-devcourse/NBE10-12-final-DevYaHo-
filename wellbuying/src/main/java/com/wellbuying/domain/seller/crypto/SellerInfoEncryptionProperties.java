package com.wellbuying.domain.seller.crypto;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

// SellerInfo 금융정보(계좌번호/예금주명) 암호화 키 - Base64로 인코딩된 AES-256(32바이트) 키
// 컴팩트 생성자에서 키 형식을 즉시 검증해 애플리케이션 기동 시점에 Fail-Fast 되도록 한다.
// SellerInfoFieldConverter는 이 검증이 끝난 값을 신뢰하고 사용한다(검증 책임 분리)
@ConfigurationProperties(prefix = "seller-info.encryption")
public record SellerInfoEncryptionProperties(String key) {

    private static final int KEY_LENGTH_BYTES = 32;

    public SellerInfoEncryptionProperties {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("SellerInfo 암호화 키(seller-info.encryption.key)가 설정되지 않았습니다.");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SellerInfo 암호화 키가 Base64 형식이 아닙니다.", e);
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "SellerInfo 암호화 키 길이가 올바르지 않습니다. AES-256에는 " + KEY_LENGTH_BYTES
                            + "바이트 키가 필요합니다(현재 " + decoded.length + "바이트).");
        }
    }

    public byte[] decodedKeyBytes() {
        return Base64.getDecoder().decode(key);
    }
}
