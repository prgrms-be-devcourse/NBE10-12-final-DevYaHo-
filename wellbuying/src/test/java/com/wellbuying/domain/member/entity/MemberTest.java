package com.wellbuying.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
