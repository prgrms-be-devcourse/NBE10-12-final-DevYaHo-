package com.wellbuying.domain.address.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.domain.address.entity.BuyerAddress;
import com.wellbuying.domain.address.repository.BuyerAddressRepository;
import com.wellbuying.domain.address.service.BuyerAddressService.BuyerAddressOwner;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.global.config.CacheConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.transaction.annotation.Transactional;

// 통합 테스트 환경은 NoOpCacheTestConfig로 @Cacheable이 비활성화되어 있어(ProductServicePopularCacheTest 참고)
// 프록시 경로로는 캐시 동작을 직접 관찰할 수 없다. 대신 운영 설정(CacheConfig)으로 실제 Redis 캐시 매니저를
// 만들어, findOwner()가 반환하는 BuyerAddressOwner가 buyerAddressOwner 캐시에 왕복 저장/복원되는지
// (record 타입 그대로 복원되는지) 검증한다 - ProductServicePopularCacheTest와 동일한 범위·방식이다.
// 처음엔 이 record 대신 Long을 그대로 캐싱했는데, GenericJackson2JsonRedisSerializer가 박싱 타입을 값
// 범위에 따라 Integer로 되돌려버려 캐시 히트 시 ClassCastException이 나는 걸 바로 이 테스트로 재현하고
// record로 감싸 고쳤다 - 회귀 방지용으로 남겨둔다
@Transactional
class BuyerAddressServiceCacheTest extends AbstractIntegrationTest {

    private static final String CACHE_NAME = "buyerAddressOwner";
    private static final Long CACHE_KEY = -9_990_001L;

    @Autowired
    private BuyerAddressService buyerAddressService;

    @Autowired
    private BuyerAddressRepository buyerAddressRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private RedisCacheManager redisCacheManager;

    @BeforeEach
    void setUp() {
        redisCacheManager = (RedisCacheManager) new CacheConfig().cacheManager(redisConnectionFactory);
        redisCacheManager.initializeCaches();
    }

    @AfterEach
    void clearCache() {
        Cache cache = redisCacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void findOwner의_반환값을_Redis에_저장했다_꺼내면_BuyerAddressOwner_타입_그대로_복원된다() {
        Long memberId = memberRepository.save(
                Member.signUp("buyer-address-cache-test-" + System.nanoTime() + "@example.com",
                        "encoded-password", "캐시테스트")).getId();
        BuyerAddress buyerAddress = buyerAddressRepository.save(
                BuyerAddress.create(memberId, "서울특별시 강남구 테헤란로 123", "4층", "06234", true));

        BuyerAddressOwner owner = buyerAddressService.findOwner(buyerAddress.getId());
        assertThat(owner.memberId()).isEqualTo(memberId);

        Cache cache = redisCacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        cache.put(CACHE_KEY, owner);
        Object restored = cache.get(CACHE_KEY).get();

        assertThat(restored).isInstanceOf(BuyerAddressOwner.class);
        assertThat(((BuyerAddressOwner) restored).memberId()).isEqualTo(memberId);
    }
}
