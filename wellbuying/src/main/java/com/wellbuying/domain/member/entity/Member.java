package com.wellbuying.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
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

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "member_status")
    private MemberStatus status;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 휴면 전환 기준 - 마지막 로그인으로부터 이 개월 수가 지나면 휴면 대상 (배치 스케줄러/로그인 시점 lazy 체크 공용)
    public static final int DORMANT_THRESHOLD_MONTHS = 6;

    // lastLoginAt 갱신 쓰기를 매 로그인마다 하지 않고 이 시간 간격으로 스로틀링
    private static final long LAST_LOGIN_UPDATE_THROTTLE_HOURS = 24;

    private static final String WITHDRAWN_NAME = "탈퇴한 회원";

    protected Member() {
    }

    private Member(String email, String password, String name, Role role, String phoneNumber) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
        this.phoneNumber = phoneNumber;
        this.status = MemberStatus.ACTIVE;
    }

    // 일반(이메일/비밀번호) 회원가입 - BUYER 역할로 생성, 전화번호 없이 가입하는 기존 호출부 호환용
    public static Member signUp(String email, String encodedPassword, String name) {
        return signUp(email, encodedPassword, name, null);
    }

    // 일반(이메일/비밀번호) 회원가입 - BUYER 역할로 생성
    public static Member signUp(String email, String encodedPassword, String name, String phoneNumber) {
        return new Member(email, encodedPassword, name, Role.BUYER, phoneNumber);
    }

    // 소셜 전용 회원 생성 - 비밀번호 없이 BUYER 역할로 생성
    public static Member socialOnly(String email, String name) {
        return new Member(email, null, name, Role.BUYER, null);
    }

    // 로컬 개발용 admin 계정 시드 전용 - 가입 API를 통해서는 ADMIN으로 생성될 수 없음
    public static Member seedAdmin(String email, String encodedPassword, String name) {
        return new Member(email, encodedPassword, name, Role.ADMIN, null);
    }

    // 비밀번호가 없는(소셜 로그인 전용) 계정인지 확인
    public boolean isSocialOnly() {
        return password == null;
    }

    // 이름/프로필 이미지/전화번호 수정 - profileImage/phoneNumber가 null이면(PATCH에서 생략) 기존 값 유지
    public void updateProfile(String name, String profileImage, String phoneNumber) {
        this.name = name;
        if (profileImage != null) {
            this.profileImage = profileImage;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
    }

    // 회원 탈퇴 - 개인정보를 익명화하고 status를 WITHDRAWN으로, deletedAt을 현재 시각으로 세팅 (soft delete)
    public void withdraw() {
        this.email = "withdrawn-" + id + "@wellbuying.local";
        this.name = WITHDRAWN_NAME;
        this.profileImage = null;
        this.password = null;
        this.phoneNumber = null;
        this.status = MemberStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
    }

    // 셀러 승인 시 role을 SELLER로 변경 (거절 시에는 호출하지 않음 - role은 BUYER 유지)
    public void activateAsSeller() {
        this.role = Role.SELLER;
    }

    // lastLoginAt 갱신이 필요한지 확인 - 기록이 없거나 스로틀 시간이 지났으면 true
    public boolean needsLastLoginUpdate() {
        return lastLoginAt == null
                || lastLoginAt.isBefore(LocalDateTime.now().minusHours(LAST_LOGIN_UPDATE_THROTTLE_HOURS));
    }

    // 휴면 전환 대상인지 확인 - recordLogin()으로 lastLoginAt이 갱신되기 전, 기존 lastLoginAt 기준으로 판단해야 함
    public boolean isDormantEligible() {
        return status == MemberStatus.ACTIVE
                && lastLoginAt != null
                && lastLoginAt.isBefore(LocalDateTime.now().minusMonths(DORMANT_THRESHOLD_MONTHS));
    }

    // lastLoginAt을 현재 시각으로 갱신
    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    // 배치 스케줄러/로그인 시점 lazy 체크가 공용으로 사용하는 휴면 전환
    public void markDormant() {
        this.status = MemberStatus.DORMANT;
    }

    // 이메일 인증코드로 휴면 계정 재활성화 - status를 ACTIVE로 되돌리고 방금 로그인한 것으로 lastLoginAt 갱신
    public void reactivate() {
        this.status = MemberStatus.ACTIVE;
        recordLogin();
    }

    // 휴면 상태 검증 - 이미 DORMANT거나 이번 로그인 시점 기준 휴면 대상이면 전환 후 차단 (AuthService/OAuthAccountService 공용)
    public void validateNotDormant() {
        if (this.status == MemberStatus.DORMANT || isDormantEligible()) {
            markDormant();
            throw new BusinessException(ErrorCode.MEMBER_DORMANT);
        }
    }

    // 재활성화 코드 발송 대상 검증 - 이미 DORMANT거나 배치 미실행으로 아직 ACTIVE인 휴면 대상이면 허용(후자는 DORMANT로 동기화), 그 외에는 차단
    public void validateCanReactivate() {
        if (this.status != MemberStatus.DORMANT && !isDormantEligible()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_DORMANT);
        }
        if (this.status != MemberStatus.DORMANT) {
            markDormant();
        }
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
