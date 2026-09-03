package com.wellbuying.global.dto;

import java.util.List;

public record CursorPageResponse<T>(List<T> content, String nextCursor, boolean hasNext) {
}
