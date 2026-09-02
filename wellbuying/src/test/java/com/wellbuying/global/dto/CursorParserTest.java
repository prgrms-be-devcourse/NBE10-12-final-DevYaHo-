package com.wellbuying.global.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wellbuying.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

class CursorParserTest {

    // ── encode 정상 케이스 ──────────────────────────────────────────────

    @Test
    void encode_단일값_LATEST() {
        assertThat(CursorParser.encode("LATEST", 105L)).isEqualTo("LATEST:105");
    }

    @Test
    void encode_복합값_POPULAR() {
        assertThat(CursorParser.encode("POPULAR", 1001L, 800683316L)).isEqualTo("POPULAR:1001_800683316");
    }

    @Test
    void encode_복합값_PRICE_ASC() {
        assertThat(CursorParser.encode("PRICE_ASC", 50000, 10L)).isEqualTo("PRICE_ASC:50000_10");
    }

    @Test
    void encode_복합값_RELEVANCE_실수포함() {
        assertThat(CursorParser.encode("RELEVANCE", "12.4832", "105")).isEqualTo("RELEVANCE:12.4832_105");
    }

    // ── encode 실패 케이스 ──────────────────────────────────────────────

    @Test
    void encode_values가_0개이면_IllegalArgumentException() {
        assertThatThrownBy(() -> CursorParser.encode("LATEST"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encode_values에_null이_포함되면_IllegalArgumentException() {
        assertThatThrownBy(() -> CursorParser.encode("LATEST", (Object) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── decode 정상 케이스 ──────────────────────────────────────────────

    @Test
    void decode_LATEST_단일값() {
        String[] parts = CursorParser.decode("LATEST", "LATEST:105", 1);
        assertThat(parts).containsExactly("105");
    }

    @Test
    void decode_POPULAR_복합값() {
        String[] parts = CursorParser.decode("POPULAR", "POPULAR:1001_800683316", 2);
        assertThat(parts).containsExactly("1001", "800683316");
    }

    @Test
    void decode_PRICE_ASC_복합값() {
        String[] parts = CursorParser.decode("PRICE_ASC", "PRICE_ASC:50000_10", 2);
        assertThat(parts).containsExactly("50000", "10");
    }

    // ── decode 실패 케이스 ──────────────────────────────────────────────

    @Test
    void decode_cursor가_null이면_INVALID_CURSOR() {
        assertThatThrownBy(() -> CursorParser.decode("LATEST", null, 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_cursor가_빈문자열이면_INVALID_CURSOR() {
        assertThatThrownBy(() -> CursorParser.decode("LATEST", "", 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_콜론이_없으면_INVALID_CURSOR() {
        assertThatThrownBy(() -> CursorParser.decode("LATEST", "105", 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_prefix_불일치이면_INVALID_CURSOR() {
        // PRICE_ASC 커서를 POPULAR 요청에 재사용하는 sort 혼용 차단
        assertThatThrownBy(() -> CursorParser.decode("POPULAR", "PRICE_ASC:1001_800683316", 2))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_파트수_부족이면_INVALID_CURSOR() {
        // POPULAR(expectedParts=2)인데 "_" 없이 숫자만
        assertThatThrownBy(() -> CursorParser.decode("POPULAR", "POPULAR:1001", 2))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_파트수_초과이면_INVALID_CURSOR() {
        // LATEST(expectedParts=1)인데 복합 커서 재사용
        assertThatThrownBy(() -> CursorParser.decode("LATEST", "LATEST:100_50", 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_숫자가_아닌_파트를_parseLong하면_INVALID_CURSOR() {
        String[] parts = CursorParser.decode("LATEST", "LATEST:abc", 1);
        assertThatThrownBy(() -> CursorParser.parseLong(parts[0]))
                .isInstanceOf(BusinessException.class);
    }

    // ── encode → decode 왕복 ───────────────────────────────────────────

    @Test
    void encode_decode_왕복_LATEST_원본값_복원() {
        long id = 800683316L;
        String cursor = CursorParser.encode("LATEST", id);
        String[] parts = CursorParser.decode("LATEST", cursor, 1);
        assertThat(CursorParser.parseLong(parts[0])).isEqualTo(id);
    }

    @Test
    void encode_decode_왕복_POPULAR_원본값_복원() {
        long viewCount = 1001L;
        long id = 800683316L;
        String cursor = CursorParser.encode("POPULAR", viewCount, id);
        String[] parts = CursorParser.decode("POPULAR", cursor, 2);
        assertThat(CursorParser.parseLong(parts[0])).isEqualTo(viewCount);
        assertThat(CursorParser.parseLong(parts[1])).isEqualTo(id);
    }

    @Test
    void encode_decode_왕복_RELEVANCE_실수_원본값_복원() {
        String scoreStr = "12.4832";
        long id = 105L;
        String cursor = CursorParser.encode("RELEVANCE", scoreStr, id);
        String[] parts = CursorParser.decode("RELEVANCE", cursor, 2);
        assertThat(CursorParser.parseDouble(parts[0])).isEqualTo(Double.parseDouble(scoreStr));
        assertThat(CursorParser.parseLong(parts[1])).isEqualTo(id);
    }
}
