package com.wellbuying.groupbuy.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.groupbuy.domain.GroupBuy;
import com.wellbuying.groupbuy.domain.GroupBuyPart;
import com.wellbuying.groupbuy.domain.GroupBuyPartStatus;
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
class GroupBuyPartRepositoryTest {

    @Autowired
    private GroupBuyRepository groupBuyRepository;

    @Autowired
    private GroupBuyPartRepository groupBuyPartRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long saveMember() {
        return memberRepository
                .save(Member.signUp("member-" + System.nanoTime() + "@example.com", "encoded-password", "회원"))
                .getId();
    }

    private Long saveGroupBuy() {
        GroupBuy groupBuy = GroupBuy.create(10L, saveMember(), "제목",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), 100, 1_000);
        return groupBuyRepository.save(groupBuy).getId();
    }

    // findByIdAndGroupBuyId가 groupBuyId까지 일치해야 조회되는지 검증 (다른 공동구매의 participation은 조회되지 않음)
    @Test
    void findByIdAndGroupBuyId는_두_조건이_모두_일치해야_조회된다() {
        Long groupBuyId = saveGroupBuy();
        Long otherGroupBuyId = saveGroupBuy();
        GroupBuyPart part = groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuyId, saveMember(), 10));

        assertThat(groupBuyPartRepository.findByIdAndGroupBuyId(part.getId(), groupBuyId)).isPresent();
        assertThat(groupBuyPartRepository.findByIdAndGroupBuyId(part.getId(), otherGroupBuyId)).isEmpty();
    }

    // findByGroupBuyIdAndMemberIdAndStatus가 CONFIRMED 상태의 내 참여만 조회하는지 검증 (취소된 참여는 조회되지 않음)
    @Test
    void findByGroupBuyIdAndMemberIdAndStatus는_상태가_일치해야_조회된다() {
        Long groupBuyId = saveGroupBuy();
        Long confirmedMemberId = saveMember();
        Long canceledMemberId = saveMember();
        GroupBuyPart confirmed = groupBuyPartRepository.save(
                GroupBuyPart.confirm(groupBuyId, confirmedMemberId, 10));
        GroupBuyPart canceled = groupBuyPartRepository.save(
                GroupBuyPart.confirm(groupBuyId, canceledMemberId, 5));
        canceled.cancel();
        groupBuyPartRepository.save(canceled);

        assertThat(groupBuyPartRepository
                .findByGroupBuyIdAndMemberIdAndStatus(groupBuyId, confirmedMemberId, GroupBuyPartStatus.CONFIRMED))
                .isPresent()
                .get()
                .extracting(GroupBuyPart::getId)
                .isEqualTo(confirmed.getId());
        assertThat(groupBuyPartRepository
                .findByGroupBuyIdAndMemberIdAndStatus(groupBuyId, canceledMemberId, GroupBuyPartStatus.CONFIRMED))
                .isEmpty();
    }

    // findByGroupBuyIdAndStatus와 countByGroupBuyIdAndStatus가 CONFIRMED 참여만 집계하는지 검증
    @Test
    void findByGroupBuyIdAndStatus는_CONFIRMED_참여만_반환한다() {
        Long groupBuyId = saveGroupBuy();
        groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuyId, saveMember(), 10));
        groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuyId, saveMember(), 5));
        GroupBuyPart canceled = groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuyId, saveMember(), 5));
        canceled.cancel();
        groupBuyPartRepository.save(canceled);

        assertThat(groupBuyPartRepository.findByGroupBuyIdAndStatus(groupBuyId, GroupBuyPartStatus.CONFIRMED))
                .hasSize(2);
        assertThat(groupBuyPartRepository.countByGroupBuyIdAndStatus(groupBuyId, GroupBuyPartStatus.CONFIRMED))
                .isEqualTo(2);
    }

    // findByGroupBuyIdInAndStatus가 여러 공동구매의 CONFIRMED 참여를 단 한 번의 쿼리로 함께 조회하는지 검증
    // (GroupBuyLifecycleScheduler가 배치 마감 처리 시 건별 반복 조회 대신 사용하는 메서드)
    @Test
    void findByGroupBuyIdInAndStatus는_여러_공동구매의_참여를_한_번에_조회한다() {
        Long groupBuyId1 = saveGroupBuy();
        Long groupBuyId2 = saveGroupBuy();
        Long otherGroupBuyId = saveGroupBuy();
        GroupBuyPart part1 = groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuyId1, saveMember(), 10));
        GroupBuyPart part2 = groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuyId2, saveMember(), 20));
        groupBuyPartRepository.save(GroupBuyPart.confirm(otherGroupBuyId, saveMember(), 30));

        var results = groupBuyPartRepository.findByGroupBuyIdInAndStatus(
                List.of(groupBuyId1, groupBuyId2), GroupBuyPartStatus.CONFIRMED);

        assertThat(results).extracting(GroupBuyPart::getId).containsExactlyInAnyOrder(part1.getId(), part2.getId());
    }

    // applyFinalPriceToConfirmedParts가 해당 공동구매의 CONFIRMED 참여자 전원에게만 최종가를 반영하고,
    // 취소된 참여나 다른 공동구매의 참여는 건드리지 않는지 검증 (GroupBuyCloseProcessor가 건별 트랜잭션에서 사용하는 벌크 UPDATE)
    @Test
    void applyFinalPriceToConfirmedParts는_해당_공동구매의_CONFIRMED_참여자에게만_최종가를_반영한다() {
        Long groupBuyId = saveGroupBuy();
        Long otherGroupBuyId = saveGroupBuy();
        GroupBuyPart confirmed1 = groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuyId, saveMember(), 10));
        GroupBuyPart confirmed2 = groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuyId, saveMember(), 20));
        GroupBuyPart canceled = groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuyId, saveMember(), 5));
        canceled.cancel();
        groupBuyPartRepository.save(canceled);
        GroupBuyPart otherGroupBuyPart = groupBuyPartRepository.save(
                GroupBuyPart.confirm(otherGroupBuyId, saveMember(), 30));

        groupBuyPartRepository.applyFinalPriceToConfirmedParts(groupBuyId, 12_000, GroupBuyPartStatus.CONFIRMED);

        assertThat(groupBuyPartRepository.findById(confirmed1.getId()).orElseThrow().getAppliedPrice())
                .isEqualTo(12_000);
        assertThat(groupBuyPartRepository.findById(confirmed2.getId()).orElseThrow().getAppliedPrice())
                .isEqualTo(12_000);
        assertThat(groupBuyPartRepository.findById(canceled.getId()).orElseThrow().getAppliedPrice()).isNull();
        assertThat(groupBuyPartRepository.findById(otherGroupBuyPart.getId()).orElseThrow().getAppliedPrice())
                .isNull();
    }
}
