package com.wellbuying.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400_INVALID_INPUT", "요청 값이 올바르지 않습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "MEMBER_409_EMAIL_DUPLICATE", "이미 사용 중인 이메일입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_404_NOT_FOUND", "존재하지 않는 회원입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_401_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 일치하지 않습니다."),
    SOCIAL_ONLY_ACCOUNT(HttpStatus.FORBIDDEN, "AUTH_403_SOCIAL_ONLY", "소셜 로그인으로 가입된 계정입니다. 소셜 로그인을 이용해주세요."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_401_REQUIRED", "인증이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_401_EXPIRED", "만료된 토큰입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "MEMBER_403_EMAIL_NOT_VERIFIED", "이메일 인증이 필요합니다."),
    EMAIL_VERIFICATION_CODE_INVALID(HttpStatus.UNAUTHORIZED, "MEMBER_401_EMAIL_CODE_INVALID", "인증 코드가 만료되었거나 일치하지 않습니다."),
    EMAIL_VERIFICATION_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "MEMBER_429_EMAIL_COOLDOWN", "잠시 후 다시 시도해주세요."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_401_REFRESH_NOT_FOUND", "세션이 만료되었습니다. 다시 로그인해주세요."),
    REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "AUTH_401_REFRESH_REUSE_DETECTED", "비정상적인 토큰 사용이 감지되어 모든 세션이 종료되었습니다. 다시 로그인해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
