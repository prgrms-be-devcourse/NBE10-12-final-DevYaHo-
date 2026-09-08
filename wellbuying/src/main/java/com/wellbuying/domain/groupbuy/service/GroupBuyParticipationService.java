package com.wellbuying.domain.groupbuy.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.address.entity.BuyerAddress;
import com.wellbuying.domain.address.repository.BuyerAddressRepository;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartCreateRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartMeResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartResponse;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupBuyParticipationService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final GroupBuyCounterRepository groupBuyCounterRepository;
    private final BuyerAddressRepository buyerAddressRepository;

    public GroupBuyParticipationService(GroupBuyRepository groupBuyRepository,
            GroupBuyPartRepository groupBuyPartRepository, GroupBuyCounterRepository groupBuyCounterRepository,
            BuyerAddressRepository buyerAddressRepository) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.groupBuyCounterRepository = groupBuyCounterRepository;
        this.buyerAddressRepository = buyerAddressRepository;
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
        if (groupBuy.isSuspended()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_SUSPENDED);
        }

        // Redis 카운터를 건드리기 전에 배송지 소유권을 먼저 검증한다 - 검증에 실패하면 카운터를 되돌릴 필요 자체가 없다
        BuyerAddress buyerAddress = buyerAddressRepository.findById(request.buyerAddressId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUYER_ADDRESS_NOT_FOUND));
        if (!buyerAddress.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.BUYER_ADDRESS_FORBIDDEN);
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
            GroupBuyPart part = groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuyId, memberId, quantity,
                    buyerAddress.getId()));

            // 자바 메모리에서 읽은 값에 더해 통째로 덮어쓰는 방식이 아니라, DB에서 직접 원자적으로 증가시킨다
            // (동시에 여러 참여가 몰려도 lost update가 없다). 이 호출 이후 영속성 컨텍스트가 비워지므로
            // 매진 판정에 쓸 최신 값은 아래에서 다시 조회해야 한다
            groupBuyRepository.increaseQuantity(groupBuyId, quantity);
            GroupBuy updatedGroupBuy = groupBuyRepository.findById(groupBuyId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));

            // 매진 판정만 여기서 즉시 하고 SUCCESS로 확정한다. 확정 참여자 전원(참여 시점이 서로 달랐던 사람들
            // 포함)에게 최종 단가를 반영하고 성사 이벤트를 기록하는 무거운 작업(참여자 수 N에 비례)은 하지 않는다 -
            // 그 작업까지 이 트랜잭션이 떠안으면 매진을 트리거한 단 하나의 요청만 N에 비례해 느려진다(부하테스트
            // 실측: 300명 규모 1.7초, 3,000명 규모 6.2초). 대신 GroupBuyFinalizationWorker가 별도 스케줄 틱에서
            // 뒤이어 처리하므로, 이 응답의 appliedPrice는 성사 트리거 여부와 무관하게 항상 null로 내려간다
            if (updatedGroupBuy.isSoldOut()) {
                updatedGroupBuy.succeed();
                // 마감 스케줄러 경로(GroupBuyCloseProcessor.closeSucceeded)와 동일하게 성사 확정 시점에
                // Redis 카운터를 즉시 정리한다 - TTL로도 결국 만료되긴 하지만, 정리 시점을 경로마다 다르게
                // 두지 않고 맞춘다
                groupBuyCounterRepository.delete(groupBuyId);
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
