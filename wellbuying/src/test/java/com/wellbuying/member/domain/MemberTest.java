package com.wellbuying.member.domain;

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

    // updateProfile() 호출 시 이름/프로필 이미지가 갱신되는지 검증
    @Test
    void updateProfile로_이름과_프로필_이미지를_수정한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");

        member.updateProfile("김철수", "https://example.com/profile.png");

        assertThat(member.getName()).isEqualTo("김철수");
        assertThat(member.getProfileImage()).isEqualTo("https://example.com/profile.png");
    }

    // withdraw() 호출 시 deletedAt이 세팅되는지 검증
    @Test
    void withdraw호출시_deletedAt이_세팅된다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");

        member.withdraw();

        assertThat(member.getDeletedAt()).isNotNull();
    }
}
