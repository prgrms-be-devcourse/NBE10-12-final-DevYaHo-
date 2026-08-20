package com.wellbuying.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.member.domain.Member;
import com.wellbuying.member.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    // Member.signUp()으로 만든 엔티티가 실제 DB에 저장되고, role이 기본값 BUYER로 들어가는지 검증
    @Test
    void signUp한_멤버는_role이_BUYER로_저장된다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");

        Member saved = memberRepository.save(member);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(Role.BUYER);
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
    }

    // existsByEmail이 저장된 이메일에는 true, 존재하지 않는 이메일에는 false를 반환하는지 검증
    @Test
    void 이메일_존재여부를_확인한다() {
        memberRepository.save(Member.signUp("exists@example.com", "encoded-password", "홍길동"));

        assertThat(memberRepository.existsByEmail("exists@example.com")).isTrue();
        assertThat(memberRepository.existsByEmail("none@example.com")).isFalse();
    }

    // findByEmailAndDeletedAtIsNull이 저장된 이메일로 멤버를 정상 조회하는지 검증
    @Test
    void 이메일로_삭제되지_않은_멤버를_조회한다() {
        memberRepository.save(Member.signUp("find@example.com", "encoded-password", "홍길동"));

        assertThat(memberRepository.findByEmailAndDeletedAtIsNull("find@example.com"))
                .isPresent()
                .get()
                .extracting(Member::getEmail)
                .isEqualTo("find@example.com");
    }

    // findByEmailAndDeletedAtIsNull이 존재하지 않는 이메일에는 빈 Optional을 반환하는지 검증
    @Test
    void 존재하지_않는_이메일로_조회하면_빈값을_반환한다() {
        assertThat(memberRepository.findByEmailAndDeletedAtIsNull("none@example.com")).isEmpty();
    }
}
