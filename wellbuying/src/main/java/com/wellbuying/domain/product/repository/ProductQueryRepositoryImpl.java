package com.wellbuying.domain.product.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wellbuying.domain.product.dto.ProductMineResponse;
import com.wellbuying.domain.product.entity.ProductSortType;
import com.wellbuying.domain.product.entity.ProductStatus;
import com.wellbuying.domain.product.entity.QProduct;
import com.wellbuying.domain.product.entity.QProductCount;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.global.dto.CursorPageResponse;
import com.wellbuying.global.dto.Cursor;
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

    // 판매 중인 상품을 대상으로 카테고리/가격 필터와 정렬을 적용해 커서 기반 목록 조회
    @Override
    public CursorPageResponse<ProductSummaryResponse> search(ProductSearchCondition condition, String cursor, int size) {
        List<ProductSummaryResponse> content = queryFactory
                .select(Projections.constructor(ProductSummaryResponse.class,
                        product.id,
                        product.productName,
                        product.startPrice,
                        product.thumbnailUrl,
                        productCount.viewCount))
                .from(product)
                .join(productCount).on(productCount.productId.eq(product.id))
                .where(
                        product.status.eq(ProductStatus.APPROVED),
                        categoryEq(condition.categoryId()),
                        priceGoe(condition.minPrice()),
                        priceLoe(condition.maxPrice()),
                        cursorCondition(cursor, condition.sort())
                )
                .orderBy(sortOrders(condition.sort()))
                .limit(size + 1L)
                .fetch();

        boolean hasNext = content.size() > size;
        if (hasNext) {
            content.remove(content.size() - 1);
        }

        ProductSortType sort = condition.sort() != null ? condition.sort() : ProductSortType.LATEST;
        String nextCursor = hasNext ? buildCursor(content.get(content.size() - 1), sort) : null;
        return new CursorPageResponse<>(content, nextCursor, hasNext);
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

        return toSlice(pageable, content);
    }

    private <T> Slice<T> toSlice(Pageable pageable, List<T> results) {
        boolean hasNext = results.size() > pageable.getPageSize();
        if (hasNext) {
            results.remove(results.size() - 1);
        }
        return new SliceImpl<>(results, pageable, hasNext);
    }

    private String buildCursor(ProductSummaryResponse last, ProductSortType sort) {
        return switch (sort) {
            case POPULAR -> Cursor.encode(sort.name(), last.viewCount(), last.id());
            case PRICE_ASC, PRICE_DESC -> Cursor.encode(sort.name(), last.startPrice(), last.id());
            default -> Cursor.encode(sort.name(), last.id()); // LATEST
        };
    }

    private BooleanExpression cursorCondition(String cursor, ProductSortType sort) {
        if (cursor == null) return null;
        ProductSortType resolved = sort != null ? sort : ProductSortType.LATEST;
        return switch (resolved) {
            case LATEST -> {
                Cursor c = Cursor.decode(resolved.name(), cursor, 1);
                long id = c.getLong(0);
                yield product.id.lt(id);
            }
            case POPULAR -> {
                Cursor c = Cursor.decode(resolved.name(), cursor, 2);
                long viewCount = c.getLong(0);
                long id = c.getLong(1);
                yield productCount.viewCount.lt(viewCount)
                        .or(productCount.viewCount.eq(viewCount).and(product.id.lt(id)));
            }
            case PRICE_ASC -> {
                Cursor c = Cursor.decode(resolved.name(), cursor, 2);
                int price = c.getInt(0);
                long id = c.getLong(1);
                yield product.startPrice.gt(price)
                        .or(product.startPrice.eq(price).and(product.id.lt(id)));
            }
            case PRICE_DESC -> {
                Cursor c = Cursor.decode(resolved.name(), cursor, 2);
                int price = c.getInt(0);
                long id = c.getLong(1);
                yield product.startPrice.lt(price)
                        .or(product.startPrice.eq(price).and(product.id.lt(id)));
            }
        };
    }

    // 정렬 기준별 OrderSpecifier 배열 반환 — 비-id 정렬은 id를 tiebreaker로 추가
    private OrderSpecifier<?>[] sortOrders(ProductSortType sortType) {
        ProductSortType resolved = sortType != null ? sortType : ProductSortType.LATEST;
        return switch (resolved) {
            case POPULAR -> new OrderSpecifier<?>[] {
                    productCount.viewCount.desc(),
                    product.id.desc()
            };
            case PRICE_ASC -> new OrderSpecifier<?>[] {
                    product.startPrice.asc(),
                    product.id.desc()
            };
            case PRICE_DESC -> new OrderSpecifier<?>[] {
                    product.startPrice.desc(),
                    product.id.desc()
            };
            case LATEST -> new OrderSpecifier<?>[] { product.id.desc() };
        };
    }

    private BooleanExpression categoryEq(Long categoryId) {
        return categoryId != null ? product.categoryId.eq(categoryId) : null;
    }

    private BooleanExpression priceGoe(Integer minPrice) {
        return minPrice != null ? product.startPrice.goe(minPrice) : null;
    }

    private BooleanExpression priceLoe(Integer maxPrice) {
        return maxPrice != null ? product.startPrice.loe(maxPrice) : null;
    }
}
