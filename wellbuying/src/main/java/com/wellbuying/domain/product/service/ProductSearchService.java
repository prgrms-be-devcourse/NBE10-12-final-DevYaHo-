package com.wellbuying.domain.product.service;

import com.wellbuying.domain.product.search.ProductAutocompleteResponse;
import com.wellbuying.domain.product.search.ProductSearchFilter;
import com.wellbuying.domain.product.search.ProductSearchRepository;
import com.wellbuying.domain.product.search.ProductSearchResponse;
import com.wellbuying.domain.product.search.SearchSortType;
import java.util.List;
import com.wellbuying.global.dto.CursorPageResponse;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// OpenSearch 장애 시 서킷브레이커로 차단하고 503 폴백. 상품 목록/상세/인기상품은 PostgreSQL 경로라 영향 없음
@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);
    private static final String CIRCUIT_BREAKER_NAME = "openSearchProductSearch";

    private final ProductSearchRepository productSearchRepository;

    public ProductSearchService(ProductSearchRepository productSearchRepository) {
        this.productSearchRepository = productSearchRepository;
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "searchFallback")
    public CursorPageResponse<ProductSearchResponse> search(String keyword, SearchSortType sort, String cursor, int size,
            ProductSearchFilter filter) {
        sort.validateSupported();
        return productSearchRepository.search(keyword, cursor, size, filter);
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "autocompleteFallback")
    public List<ProductAutocompleteResponse> autocomplete(String keyword) {
        return productSearchRepository.autocomplete(keyword);
    }

    // 자동완성은 실패해도 화면 흐름을 막을 필요가 없으므로 503 대신 빈 목록으로 대체
    private List<ProductAutocompleteResponse> autocompleteFallback(String keyword, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.debug("자동완성 서킷 OPEN - 호출 즉시 차단: keyword={}", keyword);
        } else {
            log.warn("자동완성 OpenSearch 호출 실패 - 빈 목록 반환: keyword={}", keyword, t);
        }
        return List.of();
    }

    // 정렬 검증 등 요청 자체가 잘못된 경우는 그대로 전달 — 서킷 실패로도 집계되지 않음(yaml ignore-exceptions)
    private CursorPageResponse<ProductSearchResponse> searchFallback(String keyword, SearchSortType sort,
            String cursor, int size, ProductSearchFilter filter, BusinessException e) {
        throw e;
    }

    // OpenSearch 장애(연결 실패, 타임아웃) 또는 서킷 open(CallNotPermittedException) 시 503으로 응답
    private CursorPageResponse<ProductSearchResponse> searchFallback(String keyword, SearchSortType sort,
            String cursor, int size, ProductSearchFilter filter, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.warn("검색 서킷 OPEN - OpenSearch 호출 없이 즉시 차단: keyword={}", keyword);
        } else {
            log.error("검색 OpenSearch 호출 실패 - 503 폴백: keyword={}", keyword, t);
        }
        throw new BusinessException(ErrorCode.SEARCH_UNAVAILABLE);
    }
}
