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
    REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "AUTH_401_REFRESH_REUSE_DETECTED", "비정상적인 토큰 사용이 감지되어 모든 세션이 종료되었습니다. 다시 로그인해주세요."),
    SELLER_APPLICATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "SELLER_409_APPLICATION_EXISTS", "이미 셀러 신청 또는 가입 이력이 있습니다."),
    OAUTH_EXCHANGE_CODE_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_401_OAUTH_CODE_INVALID", "유효하지 않거나 만료된 교환 코드입니다."),
    SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "SOCIAL_409_ALREADY_LINKED", "이미 연동된 소셜 계정입니다."),
    SOCIAL_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "SOCIAL_409_EMAIL_EXISTS", "이미 가입된 이메일입니다. 로그인 후 연동해주세요."),
    SOCIAL_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "SOCIAL_404_NOT_FOUND", "연동되지 않은 소셜 계정입니다."),
    SOCIAL_ACCOUNT_LAST_LOGIN_METHOD(HttpStatus.CONFLICT, "SOCIAL_409_LAST_LOGIN_METHOD",
            "마지막 로그인 수단은 해제할 수 없습니다. 다른 소셜 계정을 먼저 연동해주세요."),
    GROUP_BUY_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUPBUY_404_NOT_FOUND", "존재하지 않는 공동구매입니다."),
    GROUP_BUY_FORBIDDEN(HttpStatus.FORBIDDEN, "GROUPBUY_403_FORBIDDEN", "해당 공동구매에 대한 권한이 없습니다."),
    GROUP_BUY_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "GROUPBUY_400_INVALID_PERIOD", "시작일은 마감일보다 이전이어야 합니다."),
    GROUP_BUY_INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "GROUPBUY_400_INVALID_QUANTITY", "최소 수량은 최대 수량보다 클 수 없습니다."),
    GROUP_BUY_UPDATE_NOT_ALLOWED(HttpStatus.CONFLICT, "GROUPBUY_409_UPDATE_NOT_ALLOWED", "시작 전(READY) 상태에서만 정보를 수정할 수 있습니다."),
    GROUP_BUY_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "GROUPBUY_409_CANCEL_NOT_ALLOWED", "시작 전(READY) 상태에서만 취소할 수 있습니다."),
    GROUP_BUY_NOT_ONGOING(HttpStatus.CONFLICT, "GROUPBUY_409_NOT_ONGOING", "진행 중인 공동구매가 아닙니다."),
    GROUP_BUY_SOLD_OUT(HttpStatus.CONFLICT, "GROUPBUY_409_SOLD_OUT", "잔여 수량이 부족합니다."),
    GROUP_BUY_PRICE_TIER_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "GROUPBUY_500_PRICE_TIER_NOT_FOUND", "가격 구간 정보를 찾을 수 없습니다."),
    GROUP_BUY_PART_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUPBUY_404_PART_NOT_FOUND", "존재하지 않는 참여 내역입니다."),
    GROUP_BUY_PART_FORBIDDEN(HttpStatus.FORBIDDEN, "GROUPBUY_403_PART_FORBIDDEN", "해당 참여 내역에 대한 권한이 없습니다."),
    GROUP_BUY_PART_ALREADY_CANCELED(HttpStatus.CONFLICT, "GROUPBUY_409_PART_ALREADY_CANCELED", "이미 취소된 참여 내역입니다."),
    GROUP_BUY_PART_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "GROUPBUY_409_PART_CANCEL_NOT_ALLOWED", "진행 중인 공동구매만 참여를 취소할 수 있습니다."),
    GROUP_BUY_SUSPENDED(HttpStatus.CONFLICT, "GROUPBUY_409_SUSPENDED", "판매가 정지된 공동구매입니다."),
    GROUP_BUY_SUSPENSION_ALREADY_REQUESTED(HttpStatus.CONFLICT, "GROUPBUY_409_SUSPENSION_ALREADY_REQUESTED", "이미 처리 대기 중인 판매정지 요청이 있습니다."),
    GROUP_BUY_SUSPENSION_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUPBUY_404_SUSPENSION_NOT_FOUND", "존재하지 않는 판매정지 요청입니다."),
    GROUP_BUY_SUSPENSION_ALREADY_PROCESSED(HttpStatus.CONFLICT, "GROUPBUY_409_SUSPENSION_ALREADY_PROCESSED", "이미 처리된 판매정지 요청입니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "COMMON_409_DUPLICATE", "이미 존재하는 데이터입니다."),
    SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "SELLER_404_NOT_FOUND", "존재하지 않는 셀러 신청입니다."),
    SELLER_ALREADY_PROCESSED(HttpStatus.CONFLICT, "SELLER_409_ALREADY_PROCESSED", "이미 처리된 셀러 신청입니다."),
    SELLER_NOT_APPROVED(HttpStatus.CONFLICT, "SELLER_409_NOT_APPROVED", "승인된 셀러가 아닙니다."),
    SELLER_NOT_SUSPENDED(HttpStatus.CONFLICT, "SELLER_409_NOT_SUSPENDED", "정지된 셀러가 아닙니다."),
    PRODUCT_FORBIDDEN(HttpStatus.FORBIDDEN, "PRODUCT_403_SELLER_ONLY", "생산자만 상품을 등록할 수 있습니다."),
    PRODUCT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "PRODUCT_409_ALREADY_PROCESSED", "이미 처리된 상품입니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_404_CATEGORY_NOT_FOUND", "존재하지 않는 카테고리입니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_404_NOT_FOUND", "존재하지 않는 상품입니다."),
    MEMBER_DORMANT(HttpStatus.FORBIDDEN, "MEMBER_403_DORMANT", "휴면 처리된 계정입니다. 이메일 인증 후 재활성화해주세요."),
    MEMBER_NOT_DORMANT(HttpStatus.CONFLICT, "MEMBER_409_NOT_DORMANT", "휴면 상태가 아닙니다."),
    SEARCH_SORT_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "SEARCH_400_SORT_NOT_SUPPORTED", "지원하지 않는 정렬 방식입니다.");

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
