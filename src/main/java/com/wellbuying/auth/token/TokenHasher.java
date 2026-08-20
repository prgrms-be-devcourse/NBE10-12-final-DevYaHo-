package com.wellbuying.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class TokenHasher {

    private static final String ALGORITHM = "SHA-256";

    // refresh token 원문을 SHA-256으로 해싱하여 64자리 hex 문자열 반환 (Redis에는 원문 대신 이 해시만 저장)
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " algorithm not available", e);
        }
    }
}
