package com.wellbuying.domain.groupbuy.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.groupbuy.domain.GroupBuy;
import com.wellbuying.domain.groupbuy.domain.GroupBuyPrice;
import com.wellbuying.domain.groupbuy.domain.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.dto.GroupBuyCreateRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuyDetailResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPriceResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyStatusResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuySummaryResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyUpdateRequest;
import com.wellbuying.domain.groupbuy.domain.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.event.AfterCommitExecutor;
import com.wellbuying.domain.groupbuy.event.GroupBuyEventPublisher;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupBuyService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPriceRepository groupBuyPriceRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final GroupBuyCounterRepository groupBuyCounterRepository;
    private final GroupBuyEventPublisher groupBuyEventPublisher;
    private final MemberRepository memberRepository;

    public GroupBuyService(GroupBuyRepository groupBuyRepository, GroupBuyPriceRepository groupBuyPriceRepository,
            GroupBuyPartRepository groupBuyPartRepository, GroupBuyCounterRepository groupBuyCounterRepository,
            GroupBuyEventPublisher groupBuyEventPublisher, MemberRepository memberRepository) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyPriceRepository = groupBuyPriceRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.groupBuyCounterRepository = groupBuyCounterRepository;
        this.groupBuyEventPublisher = groupBuyEventPublisher;
        this.memberRepository = memberRepository;
    }

    // 공동구매 생성 - SELLER 역할의 회원만 생산자로 등록 가능, 가격 구간과 함께 저장하고 참여용 Redis 카운터를 0으로 초기화
    @Transactional
    public GroupBuyDetailResponse create(Long producerId, GroupBuyCreateRequest request) {
        Member producer = memberRepository.findByIdAndDeletedAtIsNull(producerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (producer.getRole() != Role.SELLER) {
            throw new BusinessException(ErrorCode.GROUP_BUY_FORBIDDEN);
        }
        if (!request.startAt().isBefore(request.endAt())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_INVALID_PERIOD);
        }
        if (request.minQuantity() > request.maxQuantity()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_INVALID_QUANTITY);
        }

        GroupBuy groupBuy = groupBuyRepository.save(GroupBuy.create(request.productId(), producerId,
                request.title(), request.startAt(), request.endAt(), request.minQuantity(), request.maxQuantity()));

        List<GroupBuyPrice> priceTiers = request.priceTiers().stream()
                .map(tier -> GroupBuyPrice.of(groupBuy.getId(), tier.tierOrder(), tier.thresholdQuantity(),
                        tier.unitPrice()))
                .toList();
        groupBuyPriceRepository.saveAll(priceTiers);

        Duration ttl = Duration.between(LocalDateTime.now(), request.endAt()).plusDays(1);
        groupBuyCounterRepository.initialize(groupBuy.getId(), ttl);

        return GroupBuyDetailResponse.of(groupBuy, priceTiers);
    }

    @Transactional(readOnly = true)
    public GroupBuyDetailResponse getDetail(Long groupBuyId) {
        GroupBuy groupBuy = getGroupBuyOrThrow(groupBuyId);
        List<GroupBuyPrice> priceTiers = groupBuyPriceRepository.findByGroupBuyIdOrderByTierOrderAsc(groupBuyId);
        return GroupBuyDetailResponse.of(groupBuy, priceTiers);
    }

    @Transactional(readOnly = true)
    public GroupBuyStatusResponse getStatus(Long groupBuyId) {
        GroupBuy groupBuy = getGroupBuyOrThrow(groupBuyId);
        long participantCount = groupBuyPartRepository.countByGroupBuyIdAndStatus(groupBuyId,
                GroupBuyPartStatus.CONFIRMED);
        return GroupBuyStatusResponse.of(groupBuy, participantCount);
    }

    @Transactional(readOnly = true)
    public Page<GroupBuySummaryResponse> list(GroupBuyStatus status, Pageable pageable) {
        Page<GroupBuy> page = status != null
                ? groupBuyRepository.findByStatus(status, pageable)
                : groupBuyRepository.findAll(pageable);
        return page.map(GroupBuySummaryResponse::of);
    }

    // 공동구매는 생성 시 가격 구간이 최소 1개 이상 있어야 하고 이후 삭제되지 않으므로,
    // 빈 결과는 곧 공동구매가 존재하지 않는다는 뜻이다 - 존재 여부만 확인하는 별도 쿼리를 두지 않는다
    @Transactional(readOnly = true)
    public List<GroupBuyPriceResponse> getPriceTiers(Long groupBuyId) {
        List<GroupBuyPrice> priceTiers = groupBuyPriceRepository.findByGroupBuyIdOrderByTierOrderAsc(groupBuyId);
        if (priceTiers.isEmpty()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND);
        }
        return priceTiers.stream()
                .map(GroupBuyPriceResponse::of)
                .toList();
    }

    // 정보 수정 - 시작 전(READY)에만 허용
    @Transactional
    public GroupBuyDetailResponse update(Long producerId, Long groupBuyId, GroupBuyUpdateRequest request) {
        GroupBuy groupBuy = getGroupBuyOrThrow(groupBuyId);
        validateOwner(groupBuy, producerId);
        if (groupBuy.getStatus() != GroupBuyStatus.READY) {
            throw new BusinessException(ErrorCode.GROUP_BUY_UPDATE_NOT_ALLOWED);
        }
        if (request.endAt() != null && !groupBuy.getStartAt().isBefore(request.endAt())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_INVALID_PERIOD);
        }

        groupBuy.updateInfo(request.title(), request.endAt());
        List<GroupBuyPrice> priceTiers = groupBuyPriceRepository.findByGroupBuyIdOrderByTierOrderAsc(groupBuyId);
        return GroupBuyDetailResponse.of(groupBuy, priceTiers);
    }

    // 취소 - 시작 전(READY)에만 허용
    @Transactional
    public void cancel(Long producerId, Long groupBuyId) {
        GroupBuy groupBuy = getGroupBuyOrThrow(groupBuyId);
        validateOwner(groupBuy, producerId);
        if (groupBuy.getStatus() != GroupBuyStatus.READY) {
            throw new BusinessException(ErrorCode.GROUP_BUY_CANCEL_NOT_ALLOWED);
        }

        groupBuy.cancel();
        groupBuyCounterRepository.delete(groupBuyId);
        AfterCommitExecutor.run(() -> groupBuyEventPublisher.publishCanceled(groupBuy));
    }

    private GroupBuy getGroupBuyOrThrow(Long groupBuyId) {
        return groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
    }

    private void validateOwner(GroupBuy groupBuy, Long producerId) {
        if (!groupBuy.getProducerId().equals(producerId)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_FORBIDDEN);
        }
    }
}
