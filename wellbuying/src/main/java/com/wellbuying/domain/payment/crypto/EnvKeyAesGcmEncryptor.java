package com.wellbuying.domain.payment.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 환경변수로 주입한 마스터 키로 빌링키를 AES-256-GCM 암복호화한다.
//
// GCM을 쓰는 이유는 기밀성과 무결성을 함께 얻기 위해서다 - 저장된 암호문이 변조되면 복호화 단계에서
// 예외가 나므로, 잘못된 빌링키로 결제를 시도하는 상황이 생기지 않는다.
//
// IV는 매 암호화마다 새로 뽑아 암호문 앞에 붙여 저장한다. 같은 키로 IV를 재사용하면 GCM은
// 평문 복원까지 가능해질 만큼 치명적으로 깨지므로, 고정 IV는 선택지가 아니다.
//
// 저장 형식: Base64( IV(12B) || ciphertext || tag(16B) )
@Component
public class EnvKeyAesGcmEncryptor implements BillingKeyEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EnvKeyAesGcmEncryptor(@Value("${billing-key.master-key:}") String base64MasterKey) {
        // 키가 없거나 길이가 틀리면 부팅 단계에서 끊는다 - 결제 도중에 터지면 이미 승인된 건을 수습해야 한다
        if (base64MasterKey == null || base64MasterKey.isBlank()) {
            throw new IllegalStateException(
                    "billing-key.master-key가 비어 있습니다. .env.local의 BILLING_KEY_MASTER_KEY를 설정하세요");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64MasterKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("billing-key.master-key가 Base64 형식이 아닙니다", e);
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "billing-key.master-key는 32바이트여야 합니다 (현재 " + keyBytes.length + "바이트)");
        }
        this.masterKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // 원문(빌링키)이 예외 메시지로 새어나가지 않도록 원인만 감싸 던진다
            throw new IllegalStateException("빌링키 암호화 실패", e);
        }
    }

    @Override
    public String decrypt(String cipherText) {
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            if (combined.length <= IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("암호문 길이가 IV보다 짧습니다");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(combined, IV_LENGTH_BYTES, combined.length - IV_LENGTH_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("빌링키 복호화 실패", e);
        }
    }
}
