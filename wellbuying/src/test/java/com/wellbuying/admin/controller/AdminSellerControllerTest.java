package com.wellbuying.admin.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.seller.entity.SellerInfo;
import com.wellbuying.domain.seller.repository.SellerInfoRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class AdminSellerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SellerInfoRepository sellerInfoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Member saveMember(String email, Role role) {
        Member member = memberRepository.save(Member.signUp(email, passwordEncoder.encode("Pass1234!"), "홍길동"));
        ReflectionTestUtils.setField(member, "role", role);
        return memberRepository.save(member);
    }

    private SellerInfo savePendingSellerInfo(Long memberId) {
        return sellerInfoRepository.save(
                SellerInfo.apply(memberId, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어"));
    }

    private UsernamePasswordAuthenticationToken authOf(Member member) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
    }

    // ADMIN이 PENDING 상태의 셀러 신청을 승인하면 204를 반환하고 role/status가 바뀌는지 검증
    @Test
    void 관리자가_셀러_승인에_성공한다() throws Exception {
        Member admin = saveMember("admin-approve@example.com", Role.ADMIN);
        Member applicant = saveMember("seller-approve@example.com", Role.BUYER);
        SellerInfo sellerInfo = savePendingSellerInfo(applicant.getId());

        mockMvc.perform(post("/api/admin/sellers/{sellerId}/approve", sellerInfo.getId())
                        .with(authentication(authOf(admin))))
                .andExpect(status().isNoContent())
                .andDo(document("admin/seller-approve-success"));

        Member updated = memberRepository.findById(applicant.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getRole()).isEqualTo(Role.SELLER);
    }

    // ADMIN이 PENDING 상태의 셀러 신청을 거절하면 204를 반환하고 role은 유지되는지 검증
    @Test
    void 관리자가_셀러_거절에_성공한다() throws Exception {
        Member admin = saveMember("admin-reject@example.com", Role.ADMIN);
        Member applicant = saveMember("seller-reject@example.com", Role.BUYER);
        SellerInfo sellerInfo = savePendingSellerInfo(applicant.getId());

        mockMvc.perform(post("/api/admin/sellers/{sellerId}/reject", sellerInfo.getId())
                        .with(authentication(authOf(admin))))
                .andExpect(status().isNoContent())
                .andDo(document("admin/seller-reject-success"));

        Member updated = memberRepository.findById(applicant.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getRole()).isEqualTo(Role.BUYER);
    }

    // ADMIN이 아닌 회원(BUYER/SELLER)이 승인 API를 호출하면 403을 반환하는지 검증 (@PreAuthorize 최초 도입 검증 포인트)
    @Test
    void 관리자가_아니면_셀러_승인에_실패한다() throws Exception {
        Member buyer = saveMember("buyer-forbidden@example.com", Role.BUYER);
        Member applicant = saveMember("seller-forbidden@example.com", Role.BUYER);
        SellerInfo sellerInfo = savePendingSellerInfo(applicant.getId());

        mockMvc.perform(post("/api/admin/sellers/{sellerId}/approve", sellerInfo.getId())
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isForbidden());
    }

    // 인증 정보 없이 승인 API를 호출하면 401을 반환하는지 검증
    @Test
    void 인증되지_않은_요청은_셀러_승인에_실패한다() throws Exception {
        Member applicant = saveMember("seller-unauth@example.com", Role.BUYER);
        SellerInfo sellerInfo = savePendingSellerInfo(applicant.getId());

        mockMvc.perform(post("/api/admin/sellers/{sellerId}/approve", sellerInfo.getId()))
                .andExpect(status().isUnauthorized());
    }

    // 존재하지 않는 sellerId로 승인 시도 시 404와 SELLER_404_NOT_FOUND를 반환하는지 검증
    @Test
    void 존재하지_않는_셀러_신청은_승인에_실패한다() throws Exception {
        Member admin = saveMember("admin-not-found@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/admin/sellers/{sellerId}/approve", 999_999L)
                        .with(authentication(authOf(admin))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SELLER_404_NOT_FOUND"))
                .andDo(document("admin/seller-approve-not-found",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 이미 처리된(ACTIVE) 셀러 신청을 다시 승인 시도하면 409와 SELLER_409_ALREADY_PROCESSED를 반환하는지 검증
    @Test
    void 이미_처리된_셀러_신청은_승인에_실패한다() throws Exception {
        Member admin = saveMember("admin-already-processed@example.com", Role.ADMIN);
        Member applicant = saveMember("seller-already-processed@example.com", Role.BUYER);
        SellerInfo sellerInfo = savePendingSellerInfo(applicant.getId());
        sellerInfo.approve();
        sellerInfoRepository.save(sellerInfo);

        mockMvc.perform(post("/api/admin/sellers/{sellerId}/approve", sellerInfo.getId())
                        .with(authentication(authOf(admin))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELLER_409_ALREADY_PROCESSED"));
    }

    // 상태별 셀러 신청 목록 조회가 PENDING 목록만 반환하는지 검증
    @Test
    void 관리자가_PENDING_셀러_목록_조회에_성공한다() throws Exception {
        Member admin = saveMember("admin-list@example.com", Role.ADMIN);
        Member applicant = saveMember("seller-list@example.com", Role.BUYER);
        savePendingSellerInfo(applicant.getId());

        mockMvc.perform(get("/api/admin/sellers").param("status", "PENDING")
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andDo(document("admin/seller-list-success",
                        responseFields(
                                fieldWithPath("[].id").description("셀러 신청 ID"),
                                fieldWithPath("[].memberId").description("회원 ID"),
                                fieldWithPath("[].status").description("상태"),
                                fieldWithPath("[].bankName").description("은행명"),
                                fieldWithPath("[].companyName").description("사업자명").optional(),
                                fieldWithPath("[].createdAt").description("신청 일시"))));
    }
}
