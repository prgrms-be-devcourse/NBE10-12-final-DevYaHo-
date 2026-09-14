package com.wellbuying.domain.groupbuy.repository;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GroupBuyQueryRepository {

    // 상태/제목 키워드/카테고리로 공동구매 목록을 조회 - categoryId는 상품과 동일하게 자기 자신 +
    // 직계 자식 카테고리까지 포함해서 매칭한다(ProductQueryRepositoryImpl.categoryEq와 동일한 방식)
    Page<GroupBuy> search(GroupBuyStatus status, String keyword, Long categoryId, Pageable pageable);
}
