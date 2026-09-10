package com.wellbuying.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.entity.ProductCount;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductCountRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import com.wellbuying.global.config.CacheConfig;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.transaction.annotation.Transactional;

// 통합 테스트 환경은 NoOpCacheTestConfig로 @Cacheable이 비활성화되어 있어 프록시 경로로는
// 캐시 검증이 불가함. 대신 운영 설정(CacheConfig)으로 실제 Redis 캐시 매니저를 직접 만들고,
// 서비스가 실제 반환한 List<ProductSummaryResponse>를 넣었다 꺼내서 GenericJackson2JsonRedisSerializer가
// LinkedHashMap이 아닌 원래 타입으로 역직렬화하는지 검증한다. @Cacheable 프록시가 하는 일도
// 결국 반환값을 cache.put 하는 것이므로 직렬화 관점의 검증 범위는 동일하다.
@Transactional
class ProductServicePopularCacheTest extends AbstractIntegrationTest {

    private static final Long TEST_SELLER_ID = 8888L;
    private static final String CACHE_NAME = "popularProducts";
    private static final String CACHE_KEY = "top10";

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCountRepository productCountRepository;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private EntityManager entityManager;

    private RedisCacheManager redisCacheManager;

    @BeforeEach
    void setUp() {
        redisCacheManager = (RedisCacheManager) new CacheConfig().cacheManager(redisConnectionFactory);
        redisCacheManager.initializeCaches();

        entityManager.createNativeQuery(
                        "INSERT INTO members (id, email, name, role, status, created_at, updated_at) VALUES "
                                + "(:id, 'cache-test@wellbuying.com', '캐시테스트', 'BUYER', 'ACTIVE', now(), now())")
                .setParameter("id", TEST_SELLER_ID)
                .executeUpdate();
    }

    @AfterEach
    void clearCache() {
        Cache cache = redisCacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void getPopularProducts_반환값을_Redis에_저장했다_꺼내면_원래_타입으로_역직렬화된다() {
        ProductCategory category = categoryRepository.save(ProductCategory.create(null, "캐시테스트카테고리", 0));
        Product product = Product.register(TEST_SELLER_ID, category.getId(), "캐시테스트상품", "설명", 10000, "url");
        product.approve();
        product = productRepository.save(product);
        productCountRepository.save(ProductCount.init(product.getId()));

        List<ProductSummaryResponse> original = productService.getPopularProducts();
        assertThat(original).isNotEmpty();

        Cache cache = redisCacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        cache.put(CACHE_KEY, original);
        Object restored = cache.get(CACHE_KEY).get();

        assertThat(restored).isInstanceOf(List.class);
        List<?> restoredList = (List<?>) restored;
        assertThat(restoredList).hasSize(original.size());
        assertThat(restoredList.get(0)).isInstanceOf(ProductSummaryResponse.class);
        assertThat(((ProductSummaryResponse) restoredList.get(0)).productName()).isEqualTo("캐시테스트상품");
    }
}
