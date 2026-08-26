package com.wellbuying.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberTest {

    // signUp()으로 만든 멤버는 role이 BUYER이고 비밀번호가 있어 소셜 전용이 아닌지 검증
    @Test
    void signUp으로_생성한_멤버는_role이_BUYER이고_소셜전용이_아니다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");

        assertThat(member.getEmail()).isEqualTo("test@example.com");
        assertThat(member.getPassword()).isEqualTo("encoded-password");
        assertThat(member.getRole()).isEqualTo(Role.BUYER);
        assertThat(member.isSocialOnly()).isFalse();
    }

    // socialOnly()로 만든 멤버는 비밀번호가 null이고 isSocialOnly()가 true를 반환하는지 검증
    @Test
    void socialOnly로_생성한_멤버는_비밀번호가_없고_소셜전용이다() {
        Member member = Member.socialOnly("social@example.com", "홍길동");

        assertThat(member.getPassword()).isNull();
        assertThat(member.isSocialOnly()).isTrue();
    }

    // 4-arg signUp()으로 만든 멤버는 전화번호가 함께 저장되고 status가 ACTIVE인지 검증
    @Test
    void signUp에_전화번호를_함께_전달하면_저장된다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동", "010-1234-5678");

        assertThat(member.getPhoneNumber()).isEqualTo("010-1234-5678");
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    // updateProfile() 호출 시 이름/프로필 이미지/전화번호가 갱신되는지 검증
    @Test
    void updateProfile로_이름과_프로필_이미지와_전화번호를_수정한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");

        member.updateProfile("김철수", "https://example.com/profile.png", "010-1111-2222");

        assertThat(member.getName()).isEqualTo("김철수");
        assertThat(member.getProfileImage()).isEqualTo("https://example.com/profile.png");
        assertThat(member.getPhoneNumber()).isEqualTo("010-1111-2222");
    }

    // updateProfile() 호출 시 profileImage/phoneNumber가 null이면 기존 값이 유지되는지 검증
    @Test
    void updateProfile에_프로필_이미지와_전화번호가_null이면_기존_값을_유지한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        member.updateProfile("김철수", "https://example.com/profile.png", "010-1111-2222");

        member.updateProfile("이영희", null, null);

        assertThat(member.getName()).isEqualTo("이영희");
        assertThat(member.getProfileImage()).isEqualTo("https://example.com/profile.png");
        assertThat(member.getPhoneNumber()).isEqualTo("010-1111-2222");
    }

    // withdraw() 호출 시 개인정보가 익명화되고 status가 WITHDRAWN, deletedAt이 세팅되는지 검증
    @Test
    void withdraw호출시_개인정보가_익명화되고_상태가_WITHDRAWN이_된다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동", "010-1234-5678");

        member.withdraw();

        assertThat(member.getDeletedAt()).isNotNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getEmail()).isNotEqualTo("test@example.com");
        assertThat(member.getName()).isEqualTo("탈퇴한 회원");
        assertThat(member.getPassword()).isNull();
        assertThat(member.getPhoneNumber()).isNull();
        assertThat(member.getProfileImage()).isNull();
    }

    // activateAsSeller() 호출 시 role이 SELLER로 바뀌는지 검증
    @Test
    void activateAsSeller호출시_role이_SELLER로_바뀐다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");

        member.activateAsSeller();

        assertThat(member.getRole()).isEqualTo(Role.SELLER);
    }

    // 마지막 로그인 기록이 없으면 갱신이 필요한지 검증
    @Test
    void lastLoginAt이_없으면_갱신이_필요하다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");

        assertThat(member.needsLastLoginUpdate()).isTrue();
    }

    // recordLogin() 호출 직후에는 스로틀 시간 내이므로 갱신이 필요없는지 검증
    @Test
    void recordLogin_직후에는_갱신이_필요없다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");

        member.recordLogin();

        assertThat(member.getLastLoginAt()).isNotNull();
        assertThat(member.needsLastLoginUpdate()).isFalse();
    }

    // lastLoginAt이 없으면 휴면 전환 대상이 아닌지 검증 (기록 자체가 없는 회원은 휴면 판정 불가)
    @Test
    void lastLoginAt이_없으면_휴면_전환_대상이_아니다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");

        assertThat(member.isDormantEligible()).isFalse();
    }

    // markDormant() 호출 시 status가 DORMANT로 바뀌는지 검증
    @Test
    void markDormant호출시_상태가_DORMANT로_바뀐다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");

        member.markDormant();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.DORMANT);
    }

    // reactivate() 호출 시 status가 ACTIVE로 바뀌고 lastLoginAt이 갱신되는지 검증
    @Test
    void reactivate호출시_상태가_ACTIVE로_바뀌고_lastLoginAt이_갱신된다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        member.markDormant();

        member.reactivate();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getLastLoginAt()).isNotNull();
    }

    // validateNotDormant() 호출 시 이미 DORMANT인 회원은 MEMBER_DORMANT 예외가 발생하는지 검증
    @Test
    void validateNotDormant호출시_이미_DORMANT면_예외가_발생한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        member.markDormant();

        assertThatThrownBy(member::validateNotDormant)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_DORMANT);
    }

    // validateNotDormant() 호출 시 배치 미실행으로 status는 ACTIVE지만 휴면 대상인 회원은 DORMANT로 전환되며 예외가 발생하는지 검증
    @Test
    void validateNotDormant호출시_휴면_전환_대상이면_DORMANT로_전환되며_예외가_발생한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        ReflectionTestUtils.setField(member, "lastLoginAt", LocalDateTime.now().minusMonths(7));

        assertThatThrownBy(member::validateNotDormant)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_DORMANT);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.DORMANT);
    }

    // validateNotDormant() 호출 시 정말로 활성 상태인 회원은 아무 일도 일어나지 않는지 검증
    @Test
    void validateNotDormant호출시_정상_활성_회원은_통과한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        member.recordLogin();

        member.validateNotDormant();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    // validateCanReactivate() 호출 시 이미 DORMANT인 회원은 예외 없이 통과하는지 검증
    @Test
    void validateCanReactivate호출시_이미_DORMANT면_통과한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        member.markDormant();

        member.validateCanReactivate();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.DORMANT);
    }

    // validateCanReactivate() 호출 시 배치 미실행으로 status는 ACTIVE지만 휴면 대상인 회원은 DORMANT로 동기화되고 예외 없이 통과하는지 검증
    @Test
    void validateCanReactivate호출시_휴면_전환_대상이면_DORMANT로_동기화되고_통과한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        ReflectionTestUtils.setField(member, "lastLoginAt", LocalDateTime.now().minusMonths(7));

        member.validateCanReactivate();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.DORMANT);
    }

    // validateCanReactivate() 호출 시 정말로 활성 상태인 회원은 MEMBER_NOT_DORMANT 예외가 발생하는지 검증
    @Test
    void validateCanReactivate호출시_정상_활성_회원은_예외가_발생한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        member.recordLogin();

        assertThatThrownBy(member::validateCanReactivate)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_DORMANT);
    }
}
