package com.wellbuying.global.exception;

// 휴면 회원 차단 전용 예외 - AuthService.login()/OAuthAccountService.findOrCreateMember()의
// noRollbackFor 대상을 BusinessException 전체가 아닌 이 예외로만 좁히기 위해 별도 타입으로 분리
public class DormantMemberException extends BusinessException {

    public DormantMemberException() {
        super(ErrorCode.MEMBER_DORMANT);
    }
}
