package com.wellbuying.domain.product.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.domain.product.search.ProductSearchRepository;
import com.wellbuying.domain.product.search.SearchSortType;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// AbstractIntegrationTest 상속 — Spring 프록시를 경유해야 @CircuitBreaker가 동작
// 테스트 (3) 생략: SearchSortType이 RELEVANCE(true) 하나뿐이라 지원 불가 정렬값 없음
class ProductSearchServiceCircuitBreakerTest extends AbstractIntegrationTest {

    @MockitoBean
    private ProductSearchRepository productSearchRepository;

    @Autowired
    private ProductSearchService service;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("openSearchProductSearch").reset();
    }

    @Test
    void search_OpenSearch_예외면_SEARCH_UNAVAILABLE로_변환한다() {
        when(productSearchRepository.search(any(), any(), anyInt(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> service.search("비타민", SearchSortType.RELEVANCE, null, 20, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SEARCH_UNAVAILABLE);
    }

    @Test
    void search_서킷이_열려있으면_OpenSearch를_호출하지_않고_SEARCH_UNAVAILABLE을_던진다() {
        circuitBreakerRegistry.circuitBreaker("openSearchProductSearch").transitionToOpenState();

        assertThatThrownBy(() -> service.search("비타민", SearchSortType.RELEVANCE, null, 20, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SEARCH_UNAVAILABLE);
        verify(productSearchRepository, never()).search(any(), any(), anyInt(), any());
    }
}