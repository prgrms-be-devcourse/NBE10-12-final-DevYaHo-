package com.wellbuying.global.config;

import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// admin-seed.enabled가 true일 때만 기동 - 운영 환경엔 해당 값을 설정하지 않는다 (docs/description/phase9-description.md § 8)
@Component
@ConditionalOnProperty(prefix = "admin-seed", name = "enabled", havingValue = "true")
public class AdminSeedRunner implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminSeedRunner(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            @Value("${admin-seed.email}") String email, @Value("${admin-seed.password}") String password) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (memberRepository.existsByEmail(email)) {
            return;
        }
        memberRepository.save(Member.seedAdmin(email, passwordEncoder.encode(password), "관리자"));
    }
}
