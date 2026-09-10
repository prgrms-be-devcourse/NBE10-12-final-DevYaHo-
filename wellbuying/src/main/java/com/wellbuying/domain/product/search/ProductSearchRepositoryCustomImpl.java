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
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;

public class ProductSearchRepositoryCustomImpl implements ProductSearchRepositoryCustom {

    private final ElasticsearchOperations operations;

    public ProductSearchRepositoryCustomImpl(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public CursorPageResponse<ProductSearchResponse> search(String keyword, SearchSortType sort, String cursor, int size, ProductSearchFilter filter) {
        List<SortOptions> sortOptions = sort == SearchSortType.POPULAR
                ? List.of(
                SortOptions.of(s -> s.field(f -> f.field("viewCount").order(SortOrder.Desc))),
                SortOptions.of(s -> s.field(f -> f.field("id").order(SortOrder.Asc))))
                : List.of(
                SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))),
                SortOptions.of(s -> s.field(f -> f.field("id").order(SortOrder.Asc))));
        NativeQueryBuilder builder = new NativeQueryBuilder()
                .withQuery(buildQuery(keyword, filter))
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

    private static final int AUTOCOMPLETE_LIMIT = 8;
    private static final String[] AUTOCOMPLETE_FIELDS = {"id", "productName"};

    // 자동완성 - 매핑 변경/재색인 없이 기존 productName 필드에 match_phrase_prefix로 접두어 매칭.
    // 커서 페이지네이션 없음(개수 고정), 상태 APPROVED 필터만 적용.
    // SourceFilter로 id/productName만 가져와 페이로드 절감, maxExpansions로 확장 범위 제한
    @Override
    public List<ProductAutocompleteResponse> autocomplete(String keyword) {
        Query query = Query.of(q -> q
                .bool(b -> b
                        .must(m -> m
                                .matchPhrasePrefix(mpp -> mpp
                                        .field("productName")
                                        .query(keyword)
                                        .maxExpansions(10)))
                        .filter(f -> f
                                .term(t -> t.field("status").value(v -> v.stringValue(ProductStatus.APPROVED.name()))))));

        NativeQuery nativeQuery = new NativeQueryBuilder()
                .withQuery(query)
                .withSourceFilter(new FetchSourceFilter(true, AUTOCOMPLETE_FIELDS, null))
                .withPageable(PageRequest.of(0, AUTOCOMPLETE_LIMIT))
                .build();

        return operations.search(nativeQuery, ProductSearchDocument.class).getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(ProductAutocompleteResponse::from)
                .toList();
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
