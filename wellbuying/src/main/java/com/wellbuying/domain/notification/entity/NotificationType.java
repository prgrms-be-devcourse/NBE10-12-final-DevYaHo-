package com.wellbuying.domain.notification.entity;

// 알림 문구는 타입에 종속된 정보라 여기서 함께 관리한다 - NotificationService는 언제 어떤 타입으로
// 알림을 보낼지만 결정하고, 실제 문구가 무엇인지는 몰라도 되게 한다
public enum NotificationType {
    GROUP_BUY_COMPLETED("공동구매가 성사되었습니다. 결제를 진행해주세요."),
    GROUP_BUY_FAILED("공동구매가 목표 수량 미달로 취소되었습니다.");

    private final String defaultMessage;

    NotificationType(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
