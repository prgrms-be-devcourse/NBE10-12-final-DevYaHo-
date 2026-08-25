package com.wellbuying.global.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminSeedRunnerTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    // admin 계정이 없으면 ADMIN role로 생성하고, 비밀번호는 인코딩해서 저장하는지 검증
    @Test
    void admin_계정이_없으면_생성한다() throws Exception {
        when(memberRepository.existsByEmail("admin@wellbuying.local")).thenReturn(false);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        AdminSeedRunner runner = new AdminSeedRunner(memberRepository, passwordEncoder,
                "admin@wellbuying.local", "raw-password");

        runner.run(null);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        Member saved = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(saved.getEmail()).isEqualTo("admin@wellbuying.local");
        org.assertj.core.api.Assertions.assertThat(saved.getPassword()).isEqualTo("encoded-password");
        org.assertj.core.api.Assertions.assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
    }

    // 이미 admin 계정이 존재하면 다시 생성하지 않는지(idempotent) 검증
    @Test
    void admin_계정이_이미_있으면_생성하지_않는다() throws Exception {
        when(memberRepository.existsByEmail("admin@wellbuying.local")).thenReturn(true);
        AdminSeedRunner runner = new AdminSeedRunner(memberRepository, passwordEncoder,
                "admin@wellbuying.local", "raw-password");

        runner.run(null);

        verify(memberRepository, never()).save(any());
    }
}
