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
import org.opensearch.client.json.JsonData;
import org.opensearch.data.client.osc.NativeQuery;
import org.opensearch.data.client.osc.NativeQueryBuilder;

public class ProductSearchRepositoryCustomImpl implements ProductSearchRepositoryCustom {

    private final ElasticsearchOperations operations;

    public ProductSearchRepositoryCustomImpl(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public CursorPageResponse<ProductSearchResponse> search(String keyword, String cursor, int size, ProductSearchFilter filter) {
        NativeQueryBuilder builder = new NativeQueryBuilder()
                .withQuery(buildQuery(keyword, filter))
                .withSort(List.of(
                        SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))),
                        SortOptions.of(s -> s.field(f -> f.field("id").order(SortOrder.Asc)))
                ))
                .withPageable(PageRequest.of(0, size + 1));

        if (cursor != null) {
            Cursor c = Cursor.decode(SearchSortType.RELEVANCE.name(), cursor, 2);
            double score = c.getDouble(0);
            long id = c.getLong(1);
            builder = builder.withSearchAfter(List.of(score, id));
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
            FieldValue scoreVal = (FieldValue) sortValues.get(0);
            FieldValue idVal = (FieldValue) sortValues.get(1);
            String scoreStr;
            if (scoreVal.isDouble()) {
                scoreStr = String.valueOf(scoreVal.doubleValue());
            } else if (scoreVal.isLong()) {
                scoreStr = String.valueOf(scoreVal.longValue());
            } else {
                throw new IllegalStateException("Unexpected FieldValue kind for score: " + scoreVal._kind());
            }
            String idStr = String.valueOf(idVal.longValue());
            nextCursor = Cursor.encode(SearchSortType.RELEVANCE.name(), scoreStr, idStr);
        }

        return new CursorPageResponse<>(content, nextCursor, hasNext);
    }

    // productName/description에 대한 형태소 분석 기반 multi_match + status:APPROVED 필터
    // filter 컨텍스트로 분리하면 status 조건이 _score에 영향 없이 캐시 가능 → 관련도 정렬 정확도 유지
    private Query buildQuery(String keyword, ProductSearchFilter filter) {
        if (filter == null) filter = ProductSearchFilter.none();
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(f -> f.term(t -> t.field("status").value(v -> v.stringValue(ProductStatus.APPROVED.name())))));
        if (filter.categoryId() != null) {
            Long cid = filter.categoryId();
            filters.add(Query.of(f -> f.term(t -> t.field("categoryId").value(v -> v.longValue(cid)))));
        }
        if (filter.hasPriceRange()) {
            Integer min = filter.minPrice();
            Integer max = filter.maxPrice();
            filters.add(Query.of(f -> f.range(r -> {
                r.field("startPrice");
                if (min != null) r.gte(JsonData.of(min));
                if (max != null) r.lte(JsonData.of(max));
                return r;
            })));
        }
        if (Boolean.TRUE.equals(filter.activeGroupBuyOnly())) {
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
