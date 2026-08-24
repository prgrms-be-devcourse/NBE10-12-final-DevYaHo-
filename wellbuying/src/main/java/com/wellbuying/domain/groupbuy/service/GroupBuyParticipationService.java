package com.wellbuying.domain.groupbuy.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.groupbuy.domain.GroupBuy;
import com.wellbuying.domain.groupbuy.domain.GroupBuyPart;
import com.wellbuying.domain.groupbuy.domain.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.domain.GroupBuyPrice;
import com.wellbuying.domain.groupbuy.domain.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartCreateRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartMeResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartResponse;
import com.wellbuying.domain.groupbuy.event.AfterCommitExecutor;
import com.wellbuying.domain.groupbuy.event.GroupBuyEventPublisher;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupBuyParticipationService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPriceRepository groupBuyPriceRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final GroupBuyCounterRepository groupBuyCounterRepository;
    private final GroupBuyEventPublisher groupBuyEventPublisher;

    public GroupBuyParticipationService(GroupBuyRepository groupBuyRepository,
            GroupBuyPriceRepository groupBuyPriceRepository, GroupBuyPartRepository groupBuyPartRepository,
            GroupBuyCounterRepository groupBuyCounterRepository, GroupBuyEventPublisher groupBuyEventPublisher) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyPriceRepository = groupBuyPriceRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.groupBuyCounterRepository = groupBuyCounterRepository;
        this.groupBuyEventPublisher = groupBuyEventPublisher;
    }

    // 참여 신청 - Redis 원자적 카운터로 재고 체크+증가를 먼저 처리한 뒤, 성공한 경우에만 DB에 CONFIRMED로 반영한다
    @Transactional
    public GroupBuyPartResponse participate(Long memberId, Long groupBuyId, GroupBuyPartCreateRequest request) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (groupBuy.getStatus() != GroupBuyStatus.ONGOING || now.isBefore(groupBuy.getStartAt())
                || !now.isBefore(groupBuy.getEndAt())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_NOT_ONGOING);
        }

        int quantity = request.quantity();
        long newTotal = groupBuyCounterRepository.tryIncrease(groupBuyId, quantity, groupBuy.getMaxQuantity());
        if (newTotal < 0) {
            throw new BusinessException(ErrorCode.GROUP_BUY_SOLD_OUT);
        }

        try {
            // 참여 시점에는 가격을 계산/저장하지 않는다 - 성사되면 최종가로 소급 확정되고,
            // 실패하면 애초에 가격이 필요 없으므로 여기서 계산하는 건 낭비다. 예상가는 프론트가
            // GET /price(구간표) + GET /status(현재 수량)로 직접 계산해 보여준다
            GroupBuyPart part = groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuyId, memberId, quantity));

            // 자바 메모리에서 읽은 값에 더해 통째로 덮어쓰는 방식이 아니라, DB에서 직접 원자적으로 증가시킨다
            // (동시에 여러 참여가 몰려도 lost update가 없다). 이 호출 이후 영속성 컨텍스트가 비워지므로
            // 매진 판정에 쓸 최신 값은 아래에서 다시 조회해야 한다
            groupBuyRepository.increaseQuantity(groupBuyId, quantity);
            GroupBuy updatedGroupBuy = groupBuyRepository.findById(groupBuyId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));

            if (updatedGroupBuy.isSoldOut()) {
                updatedGroupBuy.succeed();
                // 매진으로 확정되는 순간이므로 여기서 딱 한 번만 최종 구간 단가를 계산해
                // 확정 참여자 전원(참여 시점이 서로 달랐던 사람들 포함)에게 동일하게 채워준다
                List<GroupBuyPrice> priceTiers = groupBuyPriceRepository
                        .findByGroupBuyIdOrderByTierOrderAsc(groupBuyId);
                int finalPrice = GroupBuyPriceCalculator.resolveUnitPrice(priceTiers,
                        updatedGroupBuy.getCurrentQuantity());
                // 확정 참여자 전원에게 최종 단가를 벌크 UPDATE 한 문장으로 반영한다 - 엔티티를 조회해 하나씩
                // applyFinalPrice()로 mutate하면 참여자 수(N)만큼 dirty checking UPDATE가 나가므로,
                // 그 대신 DB에 직접 반영한다. clearAutomatically라 실행 직후 영속성 컨텍스트가 비워진다
                groupBuyPartRepository.applyFinalPriceToConfirmedParts(groupBuyId, finalPrice,
                        GroupBuyPartStatus.CONFIRMED);
                // Kafka 이벤트 발행용 확정 참여자 목록 - 위 벌크 UPDATE가 같은 트랜잭션 안에서 이미 반영된 뒤
                // 재조회하는 것이라 최종가가 그대로 채워져 있다 (여기서 다시 forEach로 mutate하면 방금 피한
                // dirty checking UPDATE가 그대로 재발하므로 절대 건드리지 않는다)
                List<GroupBuyPart> confirmedParts = groupBuyPartRepository
                        .findByGroupBuyIdAndStatus(groupBuyId, GroupBuyPartStatus.CONFIRMED);
                // 방금 저장한 part는 위 clear로 인해 confirmedParts 안의 엔티티와 별개의(detached) 객체이므로,
                // 응답에 최종가가 정확히 반영되도록 직접 채워준다 (detached라 이 mutation은 DB에 반영되지 않는다)
                part.applyFinalPrice(finalPrice);
                AfterCommitExecutor.run(() -> groupBuyEventPublisher.publishCompleted(updatedGroupBuy, confirmedParts));
            }

            return GroupBuyPartResponse.of(part);
        } catch (RuntimeException e) {
            // DB 반영이 실패하면 먼저 늘려둔 Redis 카운터를 되돌려 재고가 영구히 줄어든 상태로 남지 않도록 한다
            groupBuyCounterRepository.decrease(groupBuyId, quantity);
            throw e;
        }
    }

    // 참여 취소 - 진행 중(ONGOING)인 동안만 가능
    @Transactional
    public void cancelParticipation(Long memberId, Long groupBuyId, Long partId) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        GroupBuyPart part = groupBuyPartRepository.findByIdAndGroupBuyId(partId, groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_PART_NOT_FOUND));

        if (!part.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_PART_FORBIDDEN);
        }
        if (groupBuy.getStatus() != GroupBuyStatus.ONGOING) {
            throw new BusinessException(ErrorCode.GROUP_BUY_PART_CANCEL_NOT_ALLOWED);
        }
        if (part.getStatus() != GroupBuyPartStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.GROUP_BUY_PART_ALREADY_CANCELED);
        }

        // 순서 중요: increaseQuantity/decreaseQuantity는 @Modifying(clearAutomatically=true)라 실행 즉시
        // 영속성 컨텍스트를 통째로 비운다. part.cancel()을 먼저 호출해야(그리고 flushAutomatically로
        // 그 변경이 먼저 flush돼야) 취소 처리가 유실되지 않는다 - 순서를 바꾸면 part가 detach된 뒤라
        // cancel()을 호출해도 DB에 반영되지 않는다
        part.cancel();
        groupBuyRepository.decreaseQuantity(groupBuyId, part.getQuantity());
        groupBuyCounterRepository.decrease(groupBuyId, part.getQuantity());
    }

    @Transactional(readOnly = true)
    public GroupBuyPartMeResponse myParticipation(Long memberId, Long groupBuyId) {
        return groupBuyPartRepository
                .findByGroupBuyIdAndMemberIdAndStatus(groupBuyId, memberId, GroupBuyPartStatus.CONFIRMED)
                .map(part -> GroupBuyPartMeResponse.of(true, GroupBuyPartResponse.of(part)))
                .orElseGet(() -> GroupBuyPartMeResponse.of(false, null));
    }
}
