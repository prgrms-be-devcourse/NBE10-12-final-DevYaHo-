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
    // 한 명씩 개별 저장해서, 이미 처리된(재전달로 인한) 참여자가 있어도 나머지 참여자 처리가 막히지 않게 한다
    @Transactional
    public void notifyFailed(GroupBuyFailedPayload payload) {
        List<GroupBuyPart> confirmedParts = groupBuyPartRepository.findByGroupBuyIdAndStatus(payload.groupBuyId(),
                GroupBuyPartStatus.CONFIRMED);
        for (GroupBuyPart part : confirmedParts) {
            save(part.getMemberId(), NotificationType.GROUP_BUY_FAILED, payload.groupBuyId(), payload.productId(),
                    FAILED_MESSAGE);
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
