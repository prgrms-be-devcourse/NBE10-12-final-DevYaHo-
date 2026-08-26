package com.wellbuying.domain.product.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wellbuying.domain.product.dto.ProductMineResponse;
import com.wellbuying.domain.product.entity.ProductSortType;
import com.wellbuying.domain.product.entity.ProductStatus;
import com.wellbuying.domain.product.entity.QProduct;
import com.wellbuying.domain.product.entity.QProductCount;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

public class ProductQueryRepositoryImpl implements ProductQueryRepository {

    private static final QProduct product = QProduct.product;
    private static final QProductCount productCount = QProductCount.productCount;

    private final JPAQueryFactory queryFactory;

    public ProductQueryRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    // 판매 중인 상품을 대상으로 카테고리/가격 필터와 정렬을 적용해 목록을 조회, count 쿼리 없이 다음 페이지 존재 여부만 확인
    @Override
    public Slice<ProductSummaryResponse> search(ProductSearchCondition condition, Pageable pageable) {
        List<ProductSummaryResponse> content = queryFactory
                .select(Projections.constructor(ProductSummaryResponse.class,
                        product.id,
                        product.productName,
                        product.startPrice,
                        product.thumbnailUrl,
                        Expressions.numberTemplate(Long.class, "coalesce({0}, 0)", productCount.viewCount)))
                .from(product)
                .leftJoin(productCount).on(productCount.productId.eq(product.id))
                .where(
                        product.status.eq(ProductStatus.ON_SALE),
                        categoryEq(condition.categoryId()),
                        priceGoe(condition.minPrice()),
                        priceLoe(condition.maxPrice())
                )
                .orderBy(sortOrder(condition.sort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1L)
                .fetch();

        boolean hasNext = content.size() > pageable.getPageSize();
        if (hasNext) {
            content.remove(content.size() - 1);
        }
        return new SliceImpl<>(content, pageable, hasNext);
    }

    // 특정 판매자가 등록한 상품 전체(상태 무관)를 최신순으로 조회
    @Override
    public Slice<ProductMineResponse> findBySeller(Long sellerId, Pageable pageable) {
        List<ProductMineResponse> content = queryFactory
                .select(Projections.constructor(ProductMineResponse.class,
                        product.id,
                        product.productName,
                        product.startPrice,
                        product.thumbnailUrl,
                        product.status,
                        product.createdAt))
                .from(product)
                .where(product.sellerId.eq(sellerId))
                .orderBy(product.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1L)
                .fetch();

        boolean hasNext = content.size() > pageable.getPageSize();
        if (hasNext) {
            content.remove(content.size() - 1);
        }
        return new SliceImpl<>(content, pageable, hasNext);
    }

    // 카테고리 필터 조건 생성, categoryId가 없으면 조건에서 제외
    private BooleanExpression categoryEq(Long categoryId) {
        return categoryId != null ? product.categoryId.eq(categoryId) : null;
    }

    // 최소 가격 필터 조건 생성, minPrice가 없으면 조건에서 제외
    private BooleanExpression priceGoe(Integer minPrice) {
        return minPrice != null ? product.startPrice.goe(minPrice) : null;
    }

    // 최대 가격 필터 조건 생성, maxPrice가 없으면 조건에서 제외
    private BooleanExpression priceLoe(Integer maxPrice) {
        return maxPrice != null ? product.startPrice.loe(maxPrice) : null;
    }

    // 정렬 기준 결정, sort 값이 없으면 최신순(id 내림차순)을 기본 적용
    private OrderSpecifier<?> sortOrder(ProductSortType sortType) {
        if (sortType == null) {
            return product.id.desc();
        }
        return switch (sortType) {
            case POPULAR -> productCount.viewCount.desc().nullsLast();
            case PRICE_ASC -> product.startPrice.asc();
            case PRICE_DESC -> product.startPrice.desc();
            case LATEST -> product.id.desc();
        };
    }
}