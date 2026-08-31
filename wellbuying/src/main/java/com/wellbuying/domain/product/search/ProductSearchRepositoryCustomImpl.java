package com.wellbuying.domain.product.search;

import java.util.ArrayList;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
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
    public Slice<ProductSearchResponse> search(String keyword, SearchSortType sort, int page, int size) {
        Query searchQuery = buildQuery(keyword);

        NativeQuery nativeQuery = new NativeQueryBuilder()
                .withQuery(searchQuery)
                .withPageable(PageRequest.of(page, size))
                .withMaxResults(size + 1)
                .build();

        SearchHits<ProductSearchDocument> hits = operations.search(nativeQuery, ProductSearchDocument.class);

        List<ProductSearchResponse> content = new ArrayList<>(
                hits.stream()
                        .map(SearchHit::getContent)
                        .map(ProductSearchResponse::from)
                        .toList()
        );

        boolean hasNext = content.size() > size;
        if (hasNext) {
            content.remove(content.size() - 1);
        }

        return new SliceImpl<>(content, PageRequest.of(page, size), hasNext);
    }

    // productName/description에 대한 형태소 분석 기반 multi_match + status:APPROVED 필터
    // filter 컨텍스트로 분리하면 status 조건이 _score에 영향 없이 캐시 가능 → 관련도 정렬 정확도 유지
    private Query buildQuery(String keyword) {
        return Query.of(q -> q
                .bool(b -> b
                        .must(m -> m
                                .multiMatch(mm -> mm
                                        .query(keyword)
                                        .fields("productName", "description")))
                        .filter(f -> f
                                .term(t -> t
                                        .field("status")
                                        .value(v -> v.stringValue("APPROVED"))))));
    }
}
