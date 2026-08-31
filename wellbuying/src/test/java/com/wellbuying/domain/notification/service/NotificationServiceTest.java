package com.wellbuying.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    // 성사 이벤트는 페이로드에 이미 memberId가 있으므로, 참여자 조회 없이 바로 알림 1건을 저장한다
    @Test
    void notifyCompleted은_중복이_아니면_알림을_저장한다() {
        NotificationService service = new NotificationService(notificationRepository);
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
        NotificationService service = new NotificationService(notificationRepository);
        when(notificationRepository.existsByMemberIdAndGroupBuyIdAndType(100L, 1L,
                NotificationType.GROUP_BUY_COMPLETED)).thenReturn(true);

        service.notifyCompleted(new GroupBuyCompletedPayload(1L, 10L, 100L));

        verify(notificationRepository, never()).save(any());
    }

    // exists 확인 시점엔 없었지만(false), 동시에 들어온 다른 스레드가 먼저 커밋해 save()가 유니크 제약
    // 위반으로 실패하는 레이스 상황 - 예외가 밖으로 새지 않고 이미 처리된 것으로 흡수돼야 한다
    @Test
    void notifyCompleted은_저장_시점에_유니크_제약_위반이_나도_예외를_던지지_않는다() {
        NotificationService service = new NotificationService(notificationRepository);
        when(notificationRepository.existsByMemberIdAndGroupBuyIdAndType(100L, 1L,
                NotificationType.GROUP_BUY_COMPLETED)).thenReturn(false);
        when(notificationRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        service.notifyCompleted(new GroupBuyCompletedPayload(1L, 10L, 100L));

        verify(notificationRepository, times(1)).save(any());
    }

    // 실패 이벤트는 memberId가 없으므로, 확정 참여자 중 아직 알림을 못 받은 memberId를 NOT EXISTS
    // 서브쿼리(findUnnotifiedMemberIds)로 한 번에 조회해 saveAll로 저장하는지 검증.
    // "일부만 이미 처리됨" 같은 필터링 자체는 이제 SQL 쪽 책임이라 여기서는 결과를 그대로 쓰는지만 본다
    @Test
    void notifyFailed은_대상_전원에게_알림을_saveAll로_한_번에_저장한다() {
        NotificationService service = new NotificationService(notificationRepository);
        when(notificationRepository.findUnnotifiedMemberIds(1L, GroupBuyPartStatus.CONFIRMED,
                NotificationType.GROUP_BUY_FAILED)).thenReturn(List.of(100L, 200L));

        service.notifyFailed(new GroupBuyFailedPayload(1L, 10L));

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository, times(1)).saveAll(captor.capture());
        verify(notificationRepository, never()).save(any());
        assertThat(captor.getValue()).hasSize(2)
                .extracting(Notification::getMemberId)
                .containsExactlyInAnyOrder(100L, 200L);
    }

    // 대상이 없으면(전원 이미 처리됨) saveAll조차 호출하지 않는다(빈 리스트로 왕복하지 않음)
    @Test
    void notifyFailed은_대상이_없으면_saveAll을_호출하지_않는다() {
        NotificationService service = new NotificationService(notificationRepository);
        when(notificationRepository.findUnnotifiedMemberIds(1L, GroupBuyPartStatus.CONFIRMED,
                NotificationType.GROUP_BUY_FAILED)).thenReturn(List.of());

        service.notifyFailed(new GroupBuyFailedPayload(1L, 10L));

        verify(notificationRepository, never()).saveAll(any());
    }

    // 같은 회원이 대상 목록에 중복으로 잡혀도(이론상 방어) (member_id, group_buy_id, type) 유니크
    // 제약을 건드리지 않도록 한 건으로 합쳐 저장한다
    @Test
    void notifyFailed은_같은_회원이_중복이면_한_건으로_합쳐_저장한다() {
        NotificationService service = new NotificationService(notificationRepository);
        when(notificationRepository.findUnnotifiedMemberIds(1L, GroupBuyPartStatus.CONFIRMED,
                NotificationType.GROUP_BUY_FAILED)).thenReturn(List.of(100L, 100L));

        service.notifyFailed(new GroupBuyFailedPayload(1L, 10L));

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository, times(1)).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    // 클라이언트가 Pageable에 다른 정렬(예: ?sort=id,asc)을 실어 보내도, 리포지토리에는 항상
    // 최신순(createdAt DESC, id DESC)으로 고정된 Pageable이 전달돼야 한다 - 메서드명 정렬과
    // Pageable의 정렬이 합쳐져 꼬이는 걸 막기 위해 서비스가 정렬을 직접 강제한다
    @Test
    void getNotifications은_요청_Pageable의_정렬을_무시하고_항상_최신순으로_조회한다() {
        NotificationService service = new NotificationService(notificationRepository);
        Pageable clientRequestedSort = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));
        when(notificationRepository.findByMemberId(eq(100L), any())).thenReturn(new PageImpl<>(List.of()));

        service.getNotifications(100L, clientRequestedSort);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByMemberId(eq(100L), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    @Test
    void markAsRead은_본인_알림이_아니면_예외를_던진다() {
        NotificationService service = new NotificationService(notificationRepository);
        when(notificationRepository.findByIdAndMemberId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void markAsRead은_본인_알림이면_읽음_처리한다() {
        NotificationService service = new NotificationService(notificationRepository);
        Notification notification = Notification.of(100L, NotificationType.GROUP_BUY_COMPLETED, 1L, 10L, "메시지");
        when(notificationRepository.findByIdAndMemberId(1L, 100L)).thenReturn(Optional.of(notification));

        service.markAsRead(100L, 1L);

        assertThat(notification.isRead()).isTrue();
    }
}
