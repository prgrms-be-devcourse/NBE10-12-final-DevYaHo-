package com.wellbuying.domain.groupbuy.redis;

import java.time.Duration;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class GroupBuyCounterRepository {

    private static final String KEY_PREFIX = "gb:cnt:";
    private static final RedisScript<Long> PARTICIPATE_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/participate_groupbuy.lua"), Long.class);

    private final StringRedisTemplate redisTemplate;

    public GroupBuyCounterRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // participate_groupbuy.lua 실행 - "잔여 수량 체크 + 증가"를 원자적으로 처리 (재고 초과 시 -1 반환)
    public long tryIncrease(Long groupBuyId, int quantity, int maxQuantity) {
        Long result = redisTemplate.execute(PARTICIPATE_SCRIPT, List.of(key(groupBuyId)),
                String.valueOf(quantity), String.valueOf(maxQuantity));
        return result == null ? -1 : result;
    }

    // 참여 취소 또는 DB 반영 실패 보상 처리 시 카운터를 원복
    public void decrease(Long groupBuyId, int quantity) {
        redisTemplate.opsForValue().decrement(key(groupBuyId), quantity);
    }

    // 공동구매 생성 시 카운터를 0으로 초기화 - 마감 시각 이후 TTL로 자동 만료되도록 설정
    public void initialize(Long groupBuyId, Duration ttl) {
        Duration safeTtl = ttl.isNegative() ? Duration.ofDays(1) : ttl;
        redisTemplate.opsForValue().set(key(groupBuyId), "0", safeTtl);
    }

    // 공동구매가 취소/종료되어 더 이상 참여를 받지 않게 되면 카운터 정리
    public void delete(Long groupBuyId) {
        redisTemplate.delete(key(groupBuyId));
    }

    // 스케줄러가 한 배치에서 마감 처리하는 공동구매 전체의 카운터를 한 번의 Redis 호출로 정리 (N번의 왕복 방지)
    public void deleteAll(List<Long> groupBuyIds) {
        if (groupBuyIds.isEmpty()) {
            return;
        }
        redisTemplate.delete(groupBuyIds.stream().map(this::key).toList());
    }

    private String key(Long groupBuyId) {
        return KEY_PREFIX + groupBuyId;
    }
}
