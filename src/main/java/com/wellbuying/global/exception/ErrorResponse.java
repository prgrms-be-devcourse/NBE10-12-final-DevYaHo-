package com.wellbuying.global.exception;

public record ErrorResponse(String code, String message) {

    // ErrorCode의 기본 메시지를 그대로 사용해 에러 응답 생성
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }

    // ErrorCode의 코드는 유지하되 메시지를 커스텀 값으로 교체해 에러 응답 생성 (예: 필드 검증 실패 상세 메시지)
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message);
    }
}
