package com.wellbuying.domain.product.search;

public record OutboxStatusCount(long pending, long dead) {
    public static OutboxStatusCount empty() {
        return new OutboxStatusCount(0L, 0L);
    }
}