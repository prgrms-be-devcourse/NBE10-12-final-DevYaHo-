package com.wellbuying.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank
        @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z])(?=.*[^a-zA-Z0-9]).{8,100}$",
                message = "비밀번호는 숫자, 영문자, 특수문자를 각각 최소 1개 포함하여 8자 이상이어야 합니다.")
        String password,
        @NotBlank @Size(max = 255) String name,
        // SMS 인증 도입 전까지는 필수값으로 강제하지 않으므로 @NotBlank는 붙이지 않고, 입력 시 형식/길이만 검증
        // @Pattern은 null은 통과시키지만 빈 문자열("")은 매칭 대상이 되어 검증 실패하므로, 전체를 옵셔널 그룹으로 감싸 빈 문자열도 허용
        @Pattern(regexp = "^(01[016789]-?\\d{3,4}-?\\d{4})?$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
        @Size(max = 20)
        String phoneNumber
) {
}
