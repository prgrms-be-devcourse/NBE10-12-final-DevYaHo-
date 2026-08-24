package com.wellbuying.groupbuy.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.domain.groupbuy.domain.GroupBuy;
import com.wellbuying.domain.groupbuy.domain.GroupBuyPrice;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GroupBuyPriceRepositoryTest {

    @Autowired
    private GroupBuyRepository groupBuyRepository;

    @Autowired
    private GroupBuyPriceRepository groupBuyPriceRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long saveGroupBuy() {
        Member producer = memberRepository.save(
                Member.signUp("producer-" + System.nanoTime() + "@example.com", "encoded-password", "생산자"));
        GroupBuy groupBuy = GroupBuy.create(10L, producer.getId(), "제목",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), 100, 1_000);
        return groupBuyRepository.save(groupBuy).getId();
    }

    // findByGroupBuyIdOrderByTierOrderAsc가 tierOrder 오름차순으로 반환하는지 검증
    @Test
    void findByGroupBuyIdOrderByTierOrderAsc는_tierOrder_오름차순으로_반환한다() {
        Long groupBuyId = saveGroupBuy();
        groupBuyPriceRepository.save(GroupBuyPrice.of(groupBuyId, 2, 1_000, 12_000));
        groupBuyPriceRepository.save(GroupBuyPrice.of(groupBuyId, 1, 100, 15_000));

        var results = groupBuyPriceRepository.findByGroupBuyIdOrderByTierOrderAsc(groupBuyId);

        assertThat(results).extracting(GroupBuyPrice::getTierOrder).containsExactly(1, 2);
    }

    // findByGroupBuyIdIn이 여러 공동구매의 가격 구간을 단 한 번의 쿼리로 함께 조회하는지 검증
    // (GroupBuyLifecycleScheduler가 배치 마감 처리 시 건별 반복 조회 대신 사용하는 메서드)
    @Test
    void findByGroupBuyIdIn은_여러_공동구매의_가격_구간을_한_번에_조회한다() {
        Long groupBuyId1 = saveGroupBuy();
        Long groupBuyId2 = saveGroupBuy();
        Long otherGroupBuyId = saveGroupBuy();
        groupBuyPriceRepository.save(GroupBuyPrice.of(groupBuyId1, 1, 100, 15_000));
        groupBuyPriceRepository.save(GroupBuyPrice.of(groupBuyId2, 1, 100, 20_000));
        groupBuyPriceRepository.save(GroupBuyPrice.of(otherGroupBuyId, 1, 100, 9_999));

        var results = groupBuyPriceRepository.findByGroupBuyIdIn(List.of(groupBuyId1, groupBuyId2));

        assertThat(results).extracting(GroupBuyPrice::getUnitPrice).containsExactlyInAnyOrder(15_000, 20_000);
    }
}
