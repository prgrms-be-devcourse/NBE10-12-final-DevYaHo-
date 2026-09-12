package com.wellbuying.domain.groupbuy.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.entity.QGroupBuy;
import com.wellbuying.domain.product.entity.QProduct;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

public class GroupBuyQueryRepositoryImpl implements GroupBuyQueryRepository {

    private static final QGroupBuy groupBuy = QGroupBuy.groupBuy;
    private static final QProduct product = QProduct.product;

    // GroupBuySummaryResponse/프론트 정렬 옵션(viewCount,desc / createdAt,desc / endAt,asc)에서 실제로
    // 쓰는 프로퍼티만 화이트리스트로 허용 - 임의 프로퍼티명으로 인한 500/의도치 않은 정렬을 막는다
    private static final Set<String> ALLOWED_SORT_PROPERTIES =
            Set.of("id", "createdAt", "viewCount", "endAt", "startAt");

    private final JPAQueryFactory queryFactory;
    private final ProductCategoryRepository productCategoryRepository;

    public GroupBuyQueryRepositoryImpl(JPAQueryFactory queryFactory,
            ProductCategoryRepository productCategoryRepository) {
        this.queryFactory = queryFactory;
        this.productCategoryRepository = productCategoryRepository;
    }

    @Override
    public Page<GroupBuy> search(GroupBuyStatus status, String keyword, Long categoryId, Pageable pageable) {
        // product는 leftJoin - categoryId 필터가 없으면 상품이 존재하지 않는(레거시/삭제된) 공동구매도
        // 그대로 목록에 포함되던 기존 동작을 유지한다. inner join이면 그런 건들이 통째로 빠지게 됨
        List<GroupBuy> content = queryFactory
                .selectFrom(groupBuy)
                .leftJoin(product).on(product.id.eq(groupBuy.productId))
                .where(
                        statusEq(status),
                        titleContains(keyword),
                        categoryEq(categoryId)
                )
                .orderBy(sortOrders(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(content, pageable, () -> {
            Long total = queryFactory
                    .select(groupBuy.count())
                    .from(groupBuy)
                    .leftJoin(product).on(product.id.eq(groupBuy.productId))
                    .where(
                            statusEq(status),
                            titleContains(keyword),
                            categoryEq(categoryId)
                    )
                    .fetchOne();
            return total != null ? total : 0L;
        });
    }

    private BooleanExpression statusEq(GroupBuyStatus status) {
        return status != null ? groupBuy.status.eq(status) : null;
    }

    private BooleanExpression titleContains(String keyword) {
        return (keyword != null && !keyword.isBlank()) ? groupBuy.title.containsIgnoreCase(keyword) : null;
    }

    // 상품과 동일한 카테고리 매칭 방식 - 자기 자신 id + 직계 자식 카테고리 id들을 모아 IN 조건으로 필터링한다.
    // 그래서 최상위 카테고리로 조회해도 그 하위(leaf)로 등록된 상품의 공동구매까지 함께 조회된다.
    private BooleanExpression categoryEq(Long categoryId) {
        if (categoryId == null) return null;
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        productCategoryRepository.findAllByParentIdOrderBySortOrderAscIdAsc(categoryId)
                .forEach(child -> ids.add(child.getId()));
        return ids.size() == 1 ? product.categoryId.eq(categoryId) : product.categoryId.in(ids);
    }

    private OrderSpecifier<?>[] sortOrders(Sort sort) {
        PathBuilder<GroupBuy> pathBuilder = new PathBuilder<>(GroupBuy.class, groupBuy.getMetadata());
        OrderSpecifier<?>[] orders = sort.stream()
                .filter(order -> ALLOWED_SORT_PROPERTIES.contains(order.getProperty()))
                .map(order -> new OrderSpecifier<>(
                        order.isAscending() ? Order.ASC : Order.DESC,
                        pathBuilder.getComparable(order.getProperty(), Comparable.class)))
                .toArray(OrderSpecifier<?>[]::new);
        return orders.length > 0 ? orders : new OrderSpecifier<?>[]{groupBuy.createdAt.desc()};
    }
}
