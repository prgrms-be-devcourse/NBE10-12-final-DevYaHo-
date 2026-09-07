package com.wellbuying.global.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wellbuying.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CursorTest {

    // ── encode 정상 케이스 ──────────────────────────────────────────────

    @Test
    void encode_단일값_LATEST() {
        String cursor = Cursor.encode("LATEST", 105L);
        byte[] bytes = Base64.getUrlDecoder().decode(cursor);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("LATEST:105");
    }

    @Test
    void encode_복합값_POPULAR() {
        String cursor = Cursor.encode("POPULAR", 1001L, 800683316L);
        byte[] bytes = Base64.getUrlDecoder().decode(cursor);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("POPULAR:1001_800683316");
    }

    @Test
    void encode_복합값_PRICE_ASC() {
        String cursor = Cursor.encode("PRICE_ASC", 50000, 10L);
        byte[] bytes = Base64.getUrlDecoder().decode(cursor);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("PRICE_ASC:50000_10");
    }

    @Test
    void encode_복합값_RELEVANCE_실수포함() {
        String cursor = Cursor.encode("RELEVANCE", "12.4832", "105");
        byte[] bytes = Base64.getUrlDecoder().decode(cursor);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("RELEVANCE:12.4832_105");
    }

    // ── encode 실패 케이스 ──────────────────────────────────────────────

    @Test
    void encode_values가_0개이면_IllegalArgumentException() {
        assertThatThrownBy(() -> Cursor.encode("LATEST"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encode_values에_null이_포함되면_IllegalArgumentException() {
        assertThatThrownBy(() -> Cursor.encode("LATEST", (Object) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── decode 정상 케이스 ──────────────────────────────────────────────

    @Test
    void decode_LATEST_단일값() {
        String cursor = Cursor.encode("LATEST", 105L);
        Cursor c = Cursor.decode("LATEST", cursor, 1);
        assertThat(c.getLong(0)).isEqualTo(105L);
    }

    @Test
    void decode_POPULAR_복합값() {
        String cursor = Cursor.encode("POPULAR", 1001L, 800683316L);
        Cursor c = Cursor.decode("POPULAR", cursor, 2);
        assertThat(c.getLong(0)).isEqualTo(1001L);
        assertThat(c.getLong(1)).isEqualTo(800683316L);
    }

    @Test
    void decode_PRICE_ASC_복합값() {
        String cursor = Cursor.encode("PRICE_ASC", 50000, 10L);
        Cursor c = Cursor.decode("PRICE_ASC", cursor, 2);
        assertThat(c.getInt(0)).isEqualTo(50000);
        assertThat(c.getLong(1)).isEqualTo(10L);
    }

    // ── decode 실패 케이스 ──────────────────────────────────────────────

    @Test
    void decode_cursor가_null이면_INVALID_CURSOR() {
        assertThatThrownBy(() -> Cursor.decode("LATEST", null, 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_cursor가_빈문자열이면_INVALID_CURSOR() {
        assertThatThrownBy(() -> Cursor.decode("LATEST", "", 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_콜론이_없으면_INVALID_CURSOR() {
        // "NOCOLON" → Base64 인코딩 → 유효한 Base64지만 디코딩 결과에 ":" 없음
        String rawCursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("NOCOLON".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> Cursor.decode("LATEST", rawCursor, 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_prefix_불일치이면_INVALID_CURSOR() {
        // PRICE_ASC 커서를 POPULAR 요청에 재사용하는 sort 혼용 차단
        String cursor = Cursor.encode("PRICE_ASC", 1001L, 800683316L);
        assertThatThrownBy(() -> Cursor.decode("POPULAR", cursor, 2))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_파트수_부족이면_INVALID_CURSOR() {
        // POPULAR(expectedParts=2)인데 값 1개만 인코딩
        String cursor = Cursor.encode("POPULAR", 1001L);
        assertThatThrownBy(() -> Cursor.decode("POPULAR", cursor, 2))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_파트수_초과이면_INVALID_CURSOR() {
        // LATEST(expectedParts=1)인데 값 2개 인코딩
        String cursor = Cursor.encode("LATEST", 100L, 50L);
        assertThatThrownBy(() -> Cursor.decode("LATEST", cursor, 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_숫자가_아닌_파트를_getLong하면_INVALID_CURSOR() {
        String cursor = Cursor.encode("LATEST", "abc");
        Cursor c = Cursor.decode("LATEST", cursor, 1);
        assertThatThrownBy(() -> c.getLong(0))
                .isInstanceOf(BusinessException.class);
    }

    // ── 신규: Base64 관련 케이스 ───────────────────────────────────────

    @Test
    void decode_잘못된_Base64_형식이면_INVALID_CURSOR() {
        // "!" 는 URL-safe Base64 알파벳(A-Za-z0-9-_)에 없는 문자
        assertThatThrownBy(() -> Cursor.decode("LATEST", "not-valid!!!", 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_평문_커서는_Base64_실패로_INVALID_CURSOR() {
        // ":" (0x3a) 는 URL-safe Base64 알파벳에 없어 Illegal base64 character 3a → INVALID_CURSOR
        assertThatThrownBy(() -> Cursor.decode("PRICE_ASC", "PRICE_ASC:1001_800683316", 2))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void encode_결과가_URL_safe_Base64_형식이다() {
        String cursor = Cursor.encode("POPULAR", 1001L, 800683316L);
        // URL-safe Base64 알파벳만 포함, 패딩(=) 없음
        assertThat(cursor).matches("[A-Za-z0-9\\-_]+");
        // Base64.getUrlDecoder()로 예외 없이 디코딩 가능
        assertThat(Base64.getUrlDecoder().decode(cursor)).isNotEmpty();
    }

    // ── encode → decode 왕복 ───────────────────────────────────────────

    @Test
    void encode_decode_왕복_LATEST_원본값_복원() {
        long id = 800683316L;
        String cursor = Cursor.encode("LATEST", id);
        assertThat(Cursor.decode("LATEST", cursor, 1).getLong(0)).isEqualTo(id);
    }

    @Test
    void encode_decode_왕복_POPULAR_원본값_복원() {
        long viewCount = 1001L;
        long id = 800683316L;
        String cursor = Cursor.encode("POPULAR", viewCount, id);
        Cursor c = Cursor.decode("POPULAR", cursor, 2);
        assertThat(c.getLong(0)).isEqualTo(viewCount);
        assertThat(c.getLong(1)).isEqualTo(id);
    }

    @Test
    void encode_decode_왕복_RELEVANCE_실수_원본값_복원() {
        String scoreStr = "12.4832";
        long id = 105L;
        String cursor = Cursor.encode("RELEVANCE", scoreStr, id);
        Cursor c = Cursor.decode("RELEVANCE", cursor, 2);
        assertThat(c.getDouble(0)).isEqualTo(Double.parseDouble(scoreStr));
        assertThat(c.getLong(1)).isEqualTo(id);
    }
}
