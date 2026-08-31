package com.wellbuying.domain.notification.repository;

import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.notification.entity.Notification;
import com.wellbuying.domain.notification.entity.NotificationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 정렬은 항상 서비스가 고정해서 넘기는 Pageable에 맡긴다 - 메서드명에 OrderBy를 같이 쓰면
    // 클라이언트가 ?sort=로 보낸 정렬이 뒤에 덧붙어 정렬 조건이 꼬일 수 있다(예: id DESC, id ASC가 동시에 붙음)
    Page<Notification> findByMemberId(Long memberId, Pageable pageable);

    long countByMemberIdAndReadFalse(Long memberId);

    // 본인 알림인지 함께 검증하기 위해 memberId를 조건에 포함
    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);

    // Kafka 재처리로 같은 이벤트가 다시 소비돼도 중복 알림을 만들지 않기 위한 사전 확인용 (단건 - notifyCompleted)
    boolean existsByMemberIdAndGroupBuyIdAndType(Long memberId, Long groupBuyId, NotificationType type);

    // notifyFailed용 - 확정 참여자 조회와 "이미 알림 간 회원" 조회를 각각 왕복하는 대신 NOT EXISTS
    // 서브쿼리로 한 번에 묶어, 아직 알림을 못 받은 참여자의 memberId만 가져온다.
    // FROM 절은 GroupBuyPart를 향하지만, notification 도메인이 이미 groupbuy 도메인에 의존하는 방향
    // (NotificationService가 GroupBuyPartRepository를 참조)과 결합 방향을 맞추기 위해 여기 둔다
    @Query("""
            SELECT p.memberId FROM GroupBuyPart p
            WHERE p.groupBuyId = :groupBuyId AND p.status = :status
              AND NOT EXISTS (
                  SELECT 1 FROM Notification n
                  WHERE n.groupBuyId = :groupBuyId AND n.memberId = p.memberId AND n.type = :type
              )
            """)
    List<Long> findUnnotifiedMemberIds(@Param("groupBuyId") Long groupBuyId,
            @Param("status") GroupBuyPartStatus status, @Param("type") NotificationType type);

    // GroupBuySeedRunner가 이전에 시딩한 공동구매를 정리할 때, group_buy FK 제약 때문에 그 알림도 먼저 지워야 한다
    void deleteByGroupBuyIdIn(List<Long> groupBuyIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true WHERE n.memberId = :memberId AND n.read = false")
    void markAllAsRead(@Param("memberId") Long memberId);
}
