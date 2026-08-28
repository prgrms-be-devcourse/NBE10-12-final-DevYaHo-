package com.wellbuying.domain.seller.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

// SellerInfo.accountNumber/accountHolder 컬럼용 AES-256-GCM 암호화 컨버터
// 매 암호화마다 랜덤 IV(12바이트)를 생성해 암호문 앞에 붙여 Base64로 저장 (IV 재사용 방지)
// SellerInfoEncryptionProperties 등록은 SellerInfoCryptoConfig 참고
// 키 형식 검증(Fail-Fast)은 SellerInfoEncryptionProperties 생성 시점에 이미 끝나므로, 컨버터는 검증된 값을 신뢰하고 사용한다
@Converter(autoApply = false)
@Component
public class SellerInfoFieldConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SellerInfoFieldConverter(SellerInfoEncryptionProperties properties) {
        this.secretKey = new SecretKeySpec(properties.decodedKeyBytes(), "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SellerInfo 필드 암호화에 실패했습니다.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(dbData);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SellerInfo 필드 복호화에 실패했습니다: Base64 형식이 아닙니다.", e);
        }
        // 빈 문자열이나 손상된 데이터가 들어오면 아래에서 음수 배열 크기 예외가 나므로 미리 명확한 예외로 방어
        if (decoded.length < IV_LENGTH_BYTES) {
            throw new IllegalStateException("SellerInfo 필드 복호화에 실패했습니다: 암호화된 데이터 길이가 유효하지 않습니다.");
        }
        try {
            byte[] iv = Arrays.copyOfRange(decoded, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(decoded, IV_LENGTH_BYTES, decoded.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SellerInfo 필드 복호화에 실패했습니다.", e);
        }
    }
}
