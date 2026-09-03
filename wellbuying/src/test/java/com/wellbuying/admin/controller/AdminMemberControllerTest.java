package com.wellbuying.admin.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class AdminMemberControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Member saveMember(String email, Role role) {
        Member member = memberRepository.save(Member.signUp(email, passwordEncoder.encode("Pass1234!"), "홍길동"));
        ReflectionTestUtils.setField(member, "role", role);
        return memberRepository.save(member);
    }

    private UsernamePasswordAuthenticationToken authOf(Member member) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
    }

    // ADMIN이 회원 목록을 조회하면 200과 함께 회원 요약 정보 목록을 반환하는지 검증
    @Test
    void 관리자가_회원_목록_조회에_성공한다() throws Exception {
        Member admin = saveMember("admin-list@example.com", Role.ADMIN);
        saveMember("buyer-list@example.com", Role.BUYER);

        mockMvc.perform(get("/api/admin/members")
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andDo(document("admin/member-list-success",
                        responseFields(
                                fieldWithPath("content[].id").description("회원 ID"),
                                fieldWithPath("content[].email").description("이메일"),
                                fieldWithPath("content[].name").description("이름"),
                                fieldWithPath("content[].role").description("권한"),
                                fieldWithPath("content[].status").description("상태"),
                                fieldWithPath("content[].phoneNumber").description("전화번호").optional(),
                                fieldWithPath("content[].createdAt").description("가입 일시"),
                                fieldWithPath("page.size").description("페이지 크기"),
                                fieldWithPath("page.number").description("페이지 번호(0부터 시작)"),
                                fieldWithPath("page.totalElements").description("전체 개수"),
                                fieldWithPath("page.totalPages").description("전체 페이지 수"))));
    }

    // role 파라미터로 필터링하면 해당 role의 회원만 반환되는지 검증
    @Test
    void role로_필터링하면_해당_role의_회원만_반환된다() throws Exception {
        Member admin = saveMember("admin-filter@example.com", Role.ADMIN);
        saveMember("seller-filter@example.com", Role.SELLER);
        saveMember("buyer-filter@example.com", Role.BUYER);

        mockMvc.perform(get("/api/admin/members").param("role", "SELLER")
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].role").value("SELLER"));
    }

    // status 파라미터로 필터링하면 해당 status의 회원만 반환되는지 검증
    @Test
    void status로_필터링하면_해당_status의_회원만_반환된다() throws Exception {
        Member admin = saveMember("admin-status-filter@example.com", Role.ADMIN);
        Member withdrawn = saveMember("withdrawn-filter@example.com", Role.BUYER);
        withdrawn.withdraw();
        memberRepository.save(withdrawn);

        mockMvc.perform(get("/api/admin/members").param("status", "WITHDRAWN")
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value(MemberStatus.WITHDRAWN.name()));
    }

    // ?sort= 파라미터로 정렬 기준을 지정하면 QueryDSL 조회에도 실제로 반영되는지 검증 (기본값인 createdAt desc 하드코딩이 아닌지 확인)
    @Test
    void sort_파라미터로_지정한_기준으로_정렬된다() throws Exception {
        Member admin = saveMember("admin-sort@example.com", Role.ADMIN);
        saveMember("b-sort@example.com", Role.BUYER);
        saveMember("a-sort@example.com", Role.BUYER);

        mockMvc.perform(get("/api/admin/members").param("sort", "email,asc")
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("a-sort@example.com"))
                .andExpect(jsonPath("$.content[1].email").value("admin-sort@example.com"))
                .andExpect(jsonPath("$.content[2].email").value("b-sort@example.com"));
    }

    // 화이트리스트에 없는 sort 필드를 넘기면 에러 없이 기본 정렬(createdAt desc)로 폴백되는지 검증
    @Test
    void 허용되지_않은_sort_필드는_무시되고_기본_정렬로_조회된다() throws Exception {
        Member admin = saveMember("admin-invalid-sort@example.com", Role.ADMIN);
        saveMember("buyer-invalid-sort@example.com", Role.BUYER);

        mockMvc.perform(get("/api/admin/members").param("sort", "password,asc")
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    // ADMIN이 아닌 회원이 목록 조회 API를 호출하면 403을 반환하는지 검증
    @Test
    void 관리자가_아니면_회원_목록_조회에_실패한다() throws Exception {
        Member buyer = saveMember("buyer-forbidden@example.com", Role.BUYER);

        mockMvc.perform(get("/api/admin/members")
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isForbidden());
    }

    // 인증 정보 없이 목록 조회 API를 호출하면 401을 반환하는지 검증
    @Test
    void 인증되지_않은_요청은_회원_목록_조회에_실패한다() throws Exception {
        mockMvc.perform(get("/api/admin/members"))
                .andExpect(status().isUnauthorized());
    }
}
