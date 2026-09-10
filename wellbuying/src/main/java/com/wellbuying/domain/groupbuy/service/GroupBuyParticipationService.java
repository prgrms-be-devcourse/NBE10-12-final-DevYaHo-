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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GroupBuyParticipationService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final GroupBuyCounterRepository groupBuyCounterRepository;
    private final BuyerAddressRepository buyerAddressRepository;
    private final TransactionTemplate transactionTemplate;

    public GroupBuyParticipationService(GroupBuyRepository groupBuyRepository,
            GroupBuyPartRepository groupBuyPartRepository, GroupBuyCounterRepository groupBuyCounterRepository,
            BuyerAddressRepository buyerAddressRepository, PlatformTransactionManager transactionManager) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.groupBuyCounterRepository = groupBuyCounterRepository;
        this.buyerAddressRepository = buyerAddressRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // 참여 신청 - Redis 원자적 카운터로 재고 체크+증가를 먼저 처리한 뒤, 성공한 경우에만 DB에 CONFIRMED로 반영한다.
    // 검증(STEP1)과 DB 반영(STEP3)만 각각 짧은 트랜잭션으로 끝내 커넥션을 그때그때 반납하고, Redis 호출(STEP2)은
    // 트랜잭션 밖에서 실행한다(AuthService.login()과 동일한 이유 - 커넥션 풀이 Redis 왕복 시간만큼 묶이는 걸
    // 피함). 이 메서드 자체엔 @Transactional을 붙이지 않는다 - this.validateParticipation()/confirmParticipation()
    // 호출은 프록시를 안 타는 self-invocation이라 애초에 걸리지도 않고, TransactionTemplate으로 STEP1/STEP3
    // 각각의 경계를 명시적으로 잡는 편이 의도도 더 분명하다
    public GroupBuyPartResponse participate(Long memberId, Long groupBuyId, GroupBuyPartCreateRequest request) {
        ValidatedParticipation validated = transactionTemplate.execute(
                status -> validateParticipation(memberId, groupBuyId, request));

        int quantity = request.quantity();
        long newTotal = groupBuyCounterRepository.tryIncrease(groupBuyId, quantity, validated.maxQuantity());
        if (newTotal < 0) {
            throw new BusinessException(ErrorCode.GROUP_BUY_SOLD_OUT);
        }

        try {
            ConfirmationResult result = transactionTemplate.execute(
                    status -> confirmParticipation(memberId, groupBuyId, quantity, validated.buyerAddressId()));
            // 여기 도달했다는 것은 STEP3 트랜잭션이 이미 commit까지 성공했다는 뜻이다 - TransactionTemplate은
            // 콜백이 정상 반환된 뒤에 commit을 호출하므로, 콜백 안에서 먼저 Redis 카운터를 지우면 그 이후
            // commit이 실패했을 때(락 경합, 제약 위반 등) 이미 지워진 키에 아래 catch의 decrease()가
            // 호출되어 음수 키를 새로 만들어버릴 수 있다(그 음수를 tryIncrease가 그대로 신뢰해 재고 초과
            // 허용으로 이어짐). 그래서 매진 카운터 정리는 commit 성공이 확정된 이 시점으로 미룬다
            if (result.soldOut()) {
                groupBuyCounterRepository.delete(groupBuyId);
            }
            return result.response();
        } catch (RuntimeException e) {
            // DB 반영이 실패하면 먼저 늘려둔 Redis 카운터를 되돌려 재고가 영구히 줄어든 상태로 남지 않도록 한다.
            // decrease()는 이미 삭제된 키에도 안전하다(decrease_groupbuy.lua 참고)
            groupBuyCounterRepository.decrease(groupBuyId, quantity);
            throw e;
        }
    }

    // STEP1: groupBuy 상태 + 배송지 소유권 검증만 하고 아무것도 쓰지 않는다. 배송지 소유권은 Redis 카운터를
    // 건드리기 전에 검증한다 - 검증에 실패하면 카운터를 되돌릴 필요 자체가 없다
    private ValidatedParticipation validateParticipation(Long memberId, Long groupBuyId,
            GroupBuyPartCreateRequest request) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));

        // isSuspended() 체크를 status 체크보다 먼저 한다 - suspend()가 status도 CANCELED로 바꾸므로,
        // 순서가 바뀌면 판매정지된 건이 더 일반적인 GROUP_BUY_NOT_ONGOING으로 잘못 응답된다.
        if (groupBuy.isSuspended()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_SUSPENDED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (groupBuy.getStatus() != GroupBuyStatus.ONGOING || now.isBefore(groupBuy.getStartAt())
                || !now.isBefore(groupBuy.getEndAt())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_NOT_ONGOING);
        }

        BuyerAddress buyerAddress = buyerAddressRepository.findById(request.buyerAddressId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUYER_ADDRESS_NOT_FOUND));
        if (!buyerAddress.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.BUYER_ADDRESS_FORBIDDEN);
        }

        return new ValidatedParticipation(groupBuy.getMaxQuantity(), buyerAddress.getId());
    }

    // STEP1 결과 전달용 - Redis 호출(STEP2)에 필요한 maxQuantity와 STEP3에 필요한 buyerAddressId만 담는다.
    // groupBuy/buyerAddress 엔티티 자체를 들고 나가지 않는 이유는, STEP1의 트랜잭션이 끝나는 순간 영속성
    // 컨텍스트가 닫혀 detached 상태가 되므로 값만 꺼내 쓰는 편이 안전하기 때문이다
    private record ValidatedParticipation(int maxQuantity, Long buyerAddressId) {
    }

    // STEP3 결과 전달용 - 매진 확정 여부를 담아서, Redis 카운터 정리(참여자와 무관한 부수효과)를
    // commit 성공이 확정된 뒤(participate()에서 execute()가 정상 반환된 시점)로 미룰 수 있게 한다
    private record ConfirmationResult(GroupBuyPartResponse response, boolean soldOut) {
    }

    // STEP3: 참여 건 저장 + 수량 증가 + 매진 판정을 하나의 트랜잭션으로 묶는다
    private ConfirmationResult confirmParticipation(Long memberId, Long groupBuyId, int quantity,
            Long buyerAddressId) {
        // 참여 시점에는 가격을 계산/저장하지 않는다 - 성사되면 최종가로 소급 확정되고,
        // 실패하면 애초에 가격이 필요 없으므로 여기서 계산하는 건 낭비다. 예상가는 프론트가
        // GET /price(구간표) + GET /status(현재 수량)로 직접 계산해 보여준다
        GroupBuyPart part = groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuyId, memberId, quantity,
                buyerAddressId));

        // 자바 메모리에서 읽은 값에 더해 통째로 덮어쓰는 방식이 아니라, DB에서 직접 원자적으로 증가시킨다
        // (동시에 여러 참여가 몰려도 lost update가 없다). WHERE의 status/end_at 조건이 곧 STEP1과 STEP3
        // 사이에 상태가 바뀌지 않았는지의 최종 확인이다 - 0건 반영되면 그 사이 마감/판매정지된 것이므로
        // 실패로 처리한다. increaseQuantity 호출 이후 영속성 컨텍스트가 비워지므로 매진 판정에 쓸 최신
        // 값은 아래에서 다시 조회해야 한다
        int affected = groupBuyRepository.increaseQuantity(groupBuyId, quantity, GroupBuyStatus.ONGOING,
                LocalDateTime.now());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.GROUP_BUY_NOT_ONGOING);
        }
        GroupBuy updatedGroupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));

        // 매진 판정만 여기서 즉시 하고 SUCCESS로 확정한다. 확정 참여자 전원(참여 시점이 서로 달랐던 사람들
        // 포함)에게 최종 단가를 반영하고 성사 이벤트를 기록하는 무거운 작업(참여자 수 N에 비례)은 하지 않는다 -
        // 그 작업까지 이 트랜잭션이 떠안으면 매진을 트리거한 단 하나의 요청만 N에 비례해 느려진다(부하테스트
        // 실측: 300명 규모 1.7초, 3,000명 규모 6.2초). 대신 GroupBuyFinalizationWorker가 별도 스케줄 틱에서
        // 뒤이어 처리하므로, 이 응답의 appliedPrice는 성사 트리거 여부와 무관하게 항상 null로 내려간다.
        // Redis 카운터 정리(마감 스케줄러 경로와 동일한 시점 통일 목적)는 여기서 하지 않고 participate()가
        // 이 트랜잭션의 commit 성공을 확인한 뒤 처리한다
        boolean soldOut = updatedGroupBuy.isSoldOut();
        if (soldOut) {
            updatedGroupBuy.succeed();
        }

        return new ConfirmationResult(GroupBuyPartResponse.of(part), soldOut);
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
