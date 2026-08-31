package com.wellbuying.domain.notification.service;

import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.notification.dto.NotificationResponse;
import com.wellbuying.domain.notification.entity.Notification;
import com.wellbuying.domain.notification.entity.NotificationType;
import com.wellbuying.domain.notification.event.GroupBuyCompletedPayload;
import com.wellbuying.domain.notification.event.GroupBuyFailedPayload;
import com.wellbuying.domain.notification.repository.NotificationRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final String COMPLETED_MESSAGE = "공동구매가 성사되었습니다. 결제를 진행해주세요.";
    private static final String FAILED_MESSAGE = "공동구매가 목표 수량 미달로 취소되었습니다.";

    private final NotificationRepository notificationRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;

    public NotificationService(NotificationRepository notificationRepository,
            GroupBuyPartRepository groupBuyPartRepository) {
        this.notificationRepository = notificationRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
    }

    // 성사 이벤트는 참여자 1명당 1건 발행되므로 memberId가 이미 페이로드에 들어있다
    @Transactional
    public void notifyCompleted(GroupBuyCompletedPayload payload) {
        save(payload.memberId(), NotificationType.GROUP_BUY_COMPLETED, payload.groupBuyId(), payload.productId(),
                COMPLETED_MESSAGE);
    }

    // 실패 이벤트는 공동구매당 1건만 발행되므로, 확정 참여자 목록을 직접 조회해 각자에게 알림을 남긴다.
    // 참여자가 N명이면 건별 exists+save로는 2N번의 왕복이 나가므로, 이미 알림이 간 memberId를 한 번의
    // 쿼리로 모아 걸러낸 뒤 나머지만 saveAll로 한 번에 저장한다(재처리로 일부만 남아있어도 안전)
    @Transactional
    public void notifyFailed(GroupBuyFailedPayload payload) {
        List<GroupBuyPart> confirmedParts = groupBuyPartRepository.findByGroupBuyIdAndStatus(payload.groupBuyId(),
                GroupBuyPartStatus.CONFIRMED);
        if (confirmedParts.isEmpty()) {
            return;
        }

        Set<Long> alreadyNotifiedMemberIds = notificationRepository.findMemberIdsByGroupBuyIdAndType(
                payload.groupBuyId(), NotificationType.GROUP_BUY_FAILED);

        // 유니크 제약(member_id, group_buy_id, type) 위반으로 배치 전체가 실패하지 않도록,
        // 같은 회원이 참여자 목록에 중복으로 잡혀도 한 건만 남긴다
        List<Notification> newNotifications = confirmedParts.stream()
                .map(GroupBuyPart::getMemberId)
                .filter(memberId -> !alreadyNotifiedMemberIds.contains(memberId))
                .distinct()
                .map(memberId -> Notification.of(memberId, NotificationType.GROUP_BUY_FAILED, payload.groupBuyId(),
                        payload.productId(), FAILED_MESSAGE))
                .toList();

        if (!newNotifications.isEmpty()) {
            notificationRepository.saveAll(newNotifications);
        }
    }

    // Kafka는 at-least-once라 같은 이벤트가 재처리될 수 있어, 저장 전 존재 여부를 먼저 확인한다.
    // exists 확인과 save 사이에는 여전히 레이스가 있을 수 있는데(같은 이벤트가 거의 동시에 두 번
    // 들어오는 경우), 그 경우 유니크 제약(uq_notification_member_group_buy_type) 위반이 나므로
    // "이미 다른 스레드가 만들어놨다"는 뜻으로 보고 흡수한다 - IDENTITY 채번이라 save()가 즉시
    // INSERT를 실행하므로 이 catch 지점에서 바로 잡힌다
    private void save(Long memberId, NotificationType type, Long groupBuyId, Long productId, String message) {
        if (notificationRepository.existsByMemberIdAndGroupBuyIdAndType(memberId, groupBuyId, type)) {
            return;
        }
        try {
            notificationRepository.save(Notification.of(memberId, type, groupBuyId, productId, message));
        } catch (DataIntegrityViolationException e) {
            log.debug("동시 처리로 이미 생성된 알림이라 무시함 - memberId: {}, groupBuyId: {}, type: {}", memberId, groupBuyId,
                    type);
        }
    }

    // 정렬은 항상 최신순으로 고정한다 - 클라이언트가 넘긴 Pageable의 sort를 그대로 쓰면 리포지토리의
    // 정렬 조건과 합쳐져 꼬일 수 있어(예: id DESC/ASC가 동시에 붙음), 여기서 페이지 번호/크기만 취하고
    // 정렬은 새로 강제한다
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Long memberId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return notificationRepository.findByMemberId(memberId, sorted).map(NotificationResponse::of);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long memberId) {
        return notificationRepository.countByMemberIdAndReadFalse(memberId);
    }

    @Transactional
    public void markAsRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(Long memberId) {
        notificationRepository.markAllAsRead(memberId);
    }
}
