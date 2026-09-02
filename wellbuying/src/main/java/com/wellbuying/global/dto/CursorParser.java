package com.wellbuying.global.dto;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;

public final class CursorParser {

    private CursorParser() {}

    public static String encode(String sortTypeName, Object... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("encode requires at least one value");
        }
        StringBuilder sb = new StringBuilder(sortTypeName).append(":");
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                throw new IllegalArgumentException("encode value must not be null");
            }
            if (i > 0) sb.append("_");
            sb.append(values[i]);
        }
        return sb.toString();
    }

    // ":" 앞 prefix가 sortTypeName과 다르거나, "_" 분리 후 파트 수가 expectedParts와 다르면 INVALID_CURSOR
    public static String[] decode(String sortTypeName, String cursor, int expectedParts) {
        if (cursor == null || cursor.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
        int colonIdx = cursor.indexOf(':');
        if (colonIdx == -1) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
        if (!cursor.substring(0, colonIdx).equals(sortTypeName)) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
        String body = cursor.substring(colonIdx + 1);
        String[] parts = body.split("_", expectedParts + 1);
        if (parts.length != expectedParts) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
        return parts;
    }

    public static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }

    public static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }

    public static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
