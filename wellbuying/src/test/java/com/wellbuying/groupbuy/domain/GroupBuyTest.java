package com.wellbuying.groupbuy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GroupBuyTest {

    private GroupBuy newGroupBuy() {
        return GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), 100, 1_000);
    }

    // create()로 만든 공동구매는 READY 상태이고 누적 참여 수량이 0인지 검증
    @Test
    void 생성하면_READY_상태이고_누적_수량은_0이다() {
        GroupBuy groupBuy = newGroupBuy();

        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.READY);
        assertThat(groupBuy.getCurrentQuantity()).isEqualTo(0);
    }

    // start() 호출 시 READY -> ONGOING으로 전환되는지 검증
    @Test
    void start를_호출하면_ONGOING으로_전환된다() {
        GroupBuy groupBuy = newGroupBuy();

        groupBuy.start();

        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.ONGOING);
    }

    // cancel() 호출 시 상태와 무관하게 CANCELED로 전환되는지 검증 (호출 가능 여부 자체는 서비스 계층에서 판단)
    @Test
    void cancel을_호출하면_CANCELED로_전환된다() {
        GroupBuy groupBuy = newGroupBuy();

        groupBuy.cancel();

        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.CANCELED);
    }

    // succeed()/fail() 호출 시 각각 SUCCESS/FAILED로 전환되는지 검증
    @Test
    void succeed와_fail_호출시_상태가_전환된다() {
        GroupBuy succeeded = newGroupBuy();
        GroupBuy failed = newGroupBuy();

        succeeded.succeed();
        failed.fail();

        assertThat(succeeded.getStatus()).isEqualTo(GroupBuyStatus.SUCCESS);
        assertThat(failed.getStatus()).isEqualTo(GroupBuyStatus.FAILED);
    }

    // increaseQuantity()가 누적 참여 수량을 누적시키는지 검증
    @Test
    void increaseQuantity는_수량을_누적한다() {
        GroupBuy groupBuy = newGroupBuy();

        groupBuy.increaseQuantity(300);
        groupBuy.increaseQuantity(200);

        assertThat(groupBuy.getCurrentQuantity()).isEqualTo(500);
    }

    // decreaseQuantity()가 0 미만으로 내려가지 않도록 클램프하는지 검증
    @Test
    void decreaseQuantity는_0_미만으로_내려가지_않는다() {
        GroupBuy groupBuy = newGroupBuy();
        groupBuy.increaseQuantity(50);

        groupBuy.decreaseQuantity(200);

        assertThat(groupBuy.getCurrentQuantity()).isEqualTo(0);
    }

    // isSoldOut()이 currentQuantity가 maxQuantity 이상일 때만 true를 반환하는지 검증
    @Test
    void isSoldOut은_최대_수량_도달_여부를_판단한다() {
        GroupBuy groupBuy = newGroupBuy();

        assertThat(groupBuy.isSoldOut()).isFalse();

        groupBuy.increaseQuantity(1_000);

        assertThat(groupBuy.isSoldOut()).isTrue();
    }

    // reachedMinQuantity()가 currentQuantity가 minQuantity 이상일 때만 true를 반환하는지 검증
    @Test
    void reachedMinQuantity는_최소_수량_도달_여부를_판단한다() {
        GroupBuy groupBuy = newGroupBuy();

        groupBuy.increaseQuantity(99);
        assertThat(groupBuy.reachedMinQuantity()).isFalse();

        groupBuy.increaseQuantity(1);
        assertThat(groupBuy.reachedMinQuantity()).isTrue();
    }

    // updateInfo()는 null로 전달된 필드는 유지하고, 값이 있는 필드만 갱신하는지 검증
    @Test
    void updateInfo는_null이_아닌_필드만_갱신한다() {
        GroupBuy groupBuy = newGroupBuy();
        LocalDateTime originalEndAt = groupBuy.getEndAt();
        LocalDateTime newEndAt = originalEndAt.plusDays(3);

        groupBuy.updateInfo(null, newEndAt);
        assertThat(groupBuy.getTitle()).isEqualTo("제목");
        assertThat(groupBuy.getEndAt()).isEqualTo(newEndAt);

        groupBuy.updateInfo("새 제목", null);
        assertThat(groupBuy.getTitle()).isEqualTo("새 제목");
        assertThat(groupBuy.getEndAt()).isEqualTo(newEndAt);
    }
}
