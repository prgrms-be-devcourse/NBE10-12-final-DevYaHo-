package com.wellbuying.groupbuy.dto;

public record GroupBuyPartMeResponse(
        boolean participated,
        GroupBuyPartResponse part
) {

    public static GroupBuyPartMeResponse of(boolean participated, GroupBuyPartResponse part) {
        return new GroupBuyPartMeResponse(participated, part);
    }
}
