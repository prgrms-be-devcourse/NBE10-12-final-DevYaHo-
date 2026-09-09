package com.wellbuying.domain.product.search;

import com.wellbuying.domain.product.entity.ProductStatus;
import com.wellbuying.global.dto.CursorPageResponse;
import com.wellbuying.global.dto.Cursor;
import java.util.ArrayList;
import java.util.List;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.opensearch.data.client.osc.NativeQuery;
import org.opensearch.data.client.osc.NativeQueryBuilder;

public class ProductSearchRepositoryCustomImpl implements ProductSearchRepositoryCustom {

    private final ElasticsearchOperations operations;

    public ProductSearchRepositoryCustomImpl(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public CursorPageResponse<ProductSearchResponse> search(String keyword, SearchSortType sort, String cursor, int size, Boolean activeGroupBuyOnly) {
        List<SortOptions> sortOptions = sort == SearchSortType.POPULAR
                ? List.of(
                        SortOptions.of(s -> s.field(f -> f.field("viewCount").order(SortOrder.Desc))),
                        SortOptions.of(s -> s.field(f -> f.field("id").order(SortOrder.Asc))))
                : List.of(
                        SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))),
                        SortOptions.of(s -> s.field(f -> f.field("id").order(SortOrder.Asc))));
        NativeQueryBuilder builder = new NativeQueryBuilder()
                .withQuery(buildQuery(keyword, activeGroupBuyOnly))
                .withSort(sortOptions)
                .withPageable(PageRequest.of(0, size + 1));

        if (cursor != null) {
            Cursor c = Cursor.decode(sort.name(), cursor, 2);
            Object cursorValue = sort == SearchSortType.POPULAR ? c.getLong(0) : c.getDouble(0);
            long id = c.getLong(1);
            builder = builder.withSearchAfter(List.of(cursorValue, id));
        }

        SearchHits<ProductSearchDocument> hits = operations.search(builder.build(), ProductSearchDocument.class);

        List<SearchHit<ProductSearchDocument>> searchHits = hits.getSearchHits();
        boolean hasNext = searchHits.size() > size;

        List<ProductSearchResponse> content = searchHits.stream()
                .limit(size)
                .map(SearchHit::getContent)
                .map(ProductSearchResponse::from)
                .toList();

        String nextCursor = null;
        if (hasNext) {
            List<Object> sortValues = searchHits.get(size - 1).getSortValues();
            FieldValue primarySortVal = (FieldValue) sortValues.get(0);
            FieldValue idVal = (FieldValue) sortValues.get(1);
            String primarySortStr;
            if (primarySortVal.isDouble()) {
                primarySortStr = String.valueOf(primarySortVal.doubleValue());
            } else if (primarySortVal.isLong()) {
                primarySortStr = String.valueOf(primarySortVal.longValue());
            } else {
                throw new IllegalStateException("Unexpected FieldValue kind for primary sort field: " + primarySortVal._kind());
            }
            String idStr = String.valueOf(idVal.longValue());
            nextCursor = Cursor.encode(sort.name(), primarySortStr, idStr);
        }

        return new CursorPageResponse<>(content, nextCursor, hasNext);
    }

    // productName/description에 대한 형태소 분석 기반 multi_match + status:APPROVED 필터
    // filter 컨텍스트로 분리하면 status 조건이 _score에 영향 없이 캐시 가능 → 관련도 정렬 정확도 유지
    private Query buildQuery(String keyword, Boolean activeGroupBuyOnly) {
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(f -> f.term(t -> t.field("status").value(v -> v.stringValue(ProductStatus.APPROVED.name())))));
        if (Boolean.TRUE.equals(activeGroupBuyOnly)) {
            filters.add(Query.of(f -> f.term(t -> t.field("hasActiveGroupBuy").value(v -> v.booleanValue(true)))));
        }
        return Query.of(q -> q
                .bool(b -> b
                        .must(m -> m
                                .multiMatch(mm -> mm
                                        .query(keyword)
                                        .fields("productName", "description")))
                        .filter(filters)));
    }
}
