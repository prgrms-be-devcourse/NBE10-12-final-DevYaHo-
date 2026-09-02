package com.wellbuying.global.dto;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record Cursor(String sortType, String[] values) {

    public static String encode(String sortTypeName, Object... values) {
        if (sortTypeName == null || sortTypeName.isBlank()) {
            throw new IllegalArgumentException("sortTypeName must not be null or blank");
        }
        if (values.length == 0) {
            throw new IllegalArgumentException("encode requires at least one value");
        }
        StringBuilder sb = new StringBuilder(sortTypeName).append(":");
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) throw new IllegalArgumentException("encode value must not be null");
            if (i > 0) sb.append("_");
            sb.append(values[i]);
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static Cursor decode(String expectedSortType, String rawCursor, int expectedParts) {
        if (rawCursor == null || rawCursor.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
        String decoded;
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(rawCursor);
            decoded = new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
        int colonIdx = decoded.indexOf(':');
        if (colonIdx == -1) throw new BusinessException(ErrorCode.INVALID_CURSOR);
        if (!decoded.substring(0, colonIdx).equals(expectedSortType)) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
        String body = decoded.substring(colonIdx + 1);
        String[] parts = body.split("_", expectedParts + 1);
        if (parts.length != expectedParts) throw new BusinessException(ErrorCode.INVALID_CURSOR);
        return new Cursor(expectedSortType, parts);
    }

    public long getLong(int index) {
        try {
            return Long.parseLong(values[index]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }

    public int getInt(int index) {
        try {
            return Integer.parseInt(values[index]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }

    public double getDouble(int index) {
        try {
            return Double.parseDouble(values[index]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
