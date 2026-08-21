package com.wellbuying.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

    private final TokenHasher tokenHasher = new TokenHasher();

    // 동일한 입력값은 항상 동일한 SHA-256 해시(64자리 hex)를 생성하는지 검증
    @Test
    void 같은_입력값은_항상_같은_해시를_생성한다() {
        String hash1 = tokenHasher.hash("refresh-token-value");
        String hash2 = tokenHasher.hash("refresh-token-value");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    // 서로 다른 입력값은 서로 다른 해시를 생성하는지 검증
    @Test
    void 다른_입력값은_다른_해시를_생성한다() {
        String hash1 = tokenHasher.hash("refresh-token-value-1");
        String hash2 = tokenHasher.hash("refresh-token-value-2");

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
