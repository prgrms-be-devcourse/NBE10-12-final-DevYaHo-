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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

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

    // Kafka는 at-least-once라 같은 이벤트가 재처리될 수 있어, 저장 전 존재 여부를 먼저 확인한다
    private void save(Long memberId, NotificationType type, Long groupBuyId, Long productId, String message) {
        if (notificationRepository.existsByMemberIdAndGroupBuyIdAndType(memberId, groupBuyId, type)) {
            return;
        }
        notificationRepository.save(Notification.of(memberId, type, groupBuyId, productId, message));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Long memberId, Pageable pageable) {
        return notificationRepository.findByMemberIdOrderByCreatedAtDescIdDesc(memberId, pageable)
                .map(NotificationResponse::of);
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
