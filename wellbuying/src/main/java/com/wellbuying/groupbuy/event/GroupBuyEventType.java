package com.wellbuying.groupbuy.event;

public enum GroupBuyEventType {

    GROUP_BUY_COMPLETED("GroupBuyCompleted"),
    GROUP_BUY_FAILED("GroupBuyFailed"),
    GROUP_BUY_CANCELED("GroupBuyCanceled");

    private final String code;

    GroupBuyEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
