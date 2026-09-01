package com.wellbuying.domain.product.search;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;

public enum SearchSortType {
    RELEVANCE(true);

    private final boolean supported;

    SearchSortType(boolean supported) {
        this.supported = supported;
    }

    public void validateSupported() {
        if (!this.supported) {
            throw new BusinessException(ErrorCode.SEARCH_SORT_NOT_SUPPORTED);
        }
    }
}
