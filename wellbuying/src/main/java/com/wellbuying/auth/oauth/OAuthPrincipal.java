package com.wellbuying.auth.oauth;

import com.wellbuying.member.domain.Role;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class OAuthPrincipal implements OAuth2User {

    private final Long memberId;
    private final Role role;
    private final Map<String, Object> attributes;

    public OAuthPrincipal(Long memberId, Role role, Map<String, Object> attributes) {
        this.memberId = memberId;
        this.role = role;
        this.attributes = attributes;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }
}
