package com.wellbuying.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.notification.entity.Notification;
import com.wellbuying.domain.notification.entity.NotificationType;
import com.wellbuying.domain.notification.event.GroupBuyCompletedPayload;
import com.wellbuying.domain.notification.event.GroupBuyFailedPayload;
import com.wellbuying.domain.notification.repository.NotificationRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private GroupBuyPartRepository groupBuyPartRepository;

    // 성사 이벤트는 페이로드에 이미 memberId가 있으므로, 참여자 조회 없이 바로 알림 1건을 저장한다
    @Test
    void notifyCompleted은_중복이_아니면_알림을_저장한다() {
        NotificationService service = new NotificationService(notificationRepository, groupBuyPartRepository);
        when(notificationRepository.existsByMemberIdAndGroupBuyIdAndType(100L, 1L,
                NotificationType.GROUP_BUY_COMPLETED)).thenReturn(false);

        service.notifyCompleted(new GroupBuyCompletedPayload(1L, 10L, 100L));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getMemberId()).isEqualTo(100L);
        assertThat(saved.getGroupBuyId()).isEqualTo(1L);
        assertThat(saved.getProductId()).isEqualTo(10L);
        assertThat(saved.getType()).isEqualTo(NotificationType.GROUP_BUY_COMPLETED);
    }

    // Kafka 재처리로 같은 성사 이벤트가 다시 들어와도 이미 알림이 있으면 저장하지 않는다
    @Test
    void notifyCompleted은_이미_존재하면_저장하지_않는다() {
        NotificationService service = new NotificationService(notificationRepository, groupBuyPartRepository);
        when(notificationRepository.existsByMemberIdAndGroupBuyIdAndType(100L, 1L,
                NotificationType.GROUP_BUY_COMPLETED)).thenReturn(true);

        service.notifyCompleted(new GroupBuyCompletedPayload(1L, 10L, 100L));

        verify(notificationRepository, never()).save(any());
    }

    // 실패 이벤트는 memberId가 없으므로 확정 참여자 목록을 직접 조회해 참여자 수만큼 알림을 저장한다
    @Test
    void notifyFailed은_확정_참여자_전원에게_알림을_저장한다() {
        NotificationService service = new NotificationService(notificationRepository, groupBuyPartRepository);
        GroupBuyPart part1 = GroupBuyPart.confirm(1L, 100L, 5);
        GroupBuyPart part2 = GroupBuyPart.confirm(1L, 200L, 3);
        when(groupBuyPartRepository.findByGroupBuyIdAndStatus(1L, GroupBuyPartStatus.CONFIRMED))
                .thenReturn(List.of(part1, part2));
        when(notificationRepository.existsByMemberIdAndGroupBuyIdAndType(any(), any(), any())).thenReturn(false);

        service.notifyFailed(new GroupBuyFailedPayload(1L, 10L));

        verify(notificationRepository, times(2)).save(any());
    }

    // 참여자 중 일부가 이미 처리(재전달)된 상태여도 나머지 참여자 저장은 그대로 진행된다
    @Test
    void notifyFailed은_일부가_중복이어도_나머지는_저장한다() {
        NotificationService service = new NotificationService(notificationRepository, groupBuyPartRepository);
        GroupBuyPart part1 = GroupBuyPart.confirm(1L, 100L, 5);
        GroupBuyPart part2 = GroupBuyPart.confirm(1L, 200L, 3);
        when(groupBuyPartRepository.findByGroupBuyIdAndStatus(1L, GroupBuyPartStatus.CONFIRMED))
                .thenReturn(List.of(part1, part2));
        when(notificationRepository.existsByMemberIdAndGroupBuyIdAndType(100L, 1L, NotificationType.GROUP_BUY_FAILED))
                .thenReturn(true);
        when(notificationRepository.existsByMemberIdAndGroupBuyIdAndType(200L, 1L, NotificationType.GROUP_BUY_FAILED))
                .thenReturn(false);

        service.notifyFailed(new GroupBuyFailedPayload(1L, 10L));

        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void markAsRead은_본인_알림이_아니면_예외를_던진다() {
        NotificationService service = new NotificationService(notificationRepository, groupBuyPartRepository);
        when(notificationRepository.findByIdAndMemberId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void markAsRead은_본인_알림이면_읽음_처리한다() {
        NotificationService service = new NotificationService(notificationRepository, groupBuyPartRepository);
        Notification notification = Notification.of(100L, NotificationType.GROUP_BUY_COMPLETED, 1L, 10L, "메시지");
        when(notificationRepository.findByIdAndMemberId(1L, 100L)).thenReturn(Optional.of(notification));

        service.markAsRead(100L, 1L);

        assertThat(notification.isRead()).isTrue();
    }
}
