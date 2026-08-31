package com.wellbuying.domain.notification.repository;

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

    Page<Notification> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId, Pageable pageable);

    long countByMemberIdAndReadFalse(Long memberId);

    // 본인 알림인지 함께 검증하기 위해 memberId를 조건에 포함
    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);

    // Kafka 재처리로 같은 이벤트가 다시 소비돼도 중복 알림을 만들지 않기 위한 사전 확인용
    boolean existsByMemberIdAndGroupBuyIdAndType(Long memberId, Long groupBuyId, NotificationType type);

    // GroupBuySeedRunner가 이전에 시딩한 공동구매를 정리할 때, group_buy FK 제약 때문에 그 알림도 먼저 지워야 한다
    void deleteByGroupBuyIdIn(List<Long> groupBuyIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true WHERE n.memberId = :memberId AND n.read = false")
    void markAllAsRead(@Param("memberId") Long memberId);
}
