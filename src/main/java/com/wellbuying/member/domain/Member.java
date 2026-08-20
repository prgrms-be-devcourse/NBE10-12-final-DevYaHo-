package com.wellbuying.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    private String password;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "member_role")
    private Role role;

    @Column(name = "profile_image")
    private String profileImage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Member() {
    }

    private Member(String email, String password, String name, Role role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
    }

    // 일반(이메일/비밀번호) 회원가입 - BUYER 역할로 생성
    public static Member signUp(String email, String encodedPassword, String name) {
        return new Member(email, encodedPassword, name, Role.BUYER);
    }

    // 소셜 전용 회원 생성 - 비밀번호 없이 BUYER 역할로 생성
    public static Member socialOnly(String email, String name) {
        return new Member(email, null, name, Role.BUYER);
    }

    // 비밀번호가 없는(소셜 로그인 전용) 계정인지 확인
    public boolean isSocialOnly() {
        return password == null;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
