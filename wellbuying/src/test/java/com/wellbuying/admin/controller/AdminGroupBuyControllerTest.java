package com.wellbuying.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuySuspensionRequest;
import com.wellbuying.domain.groupbuy.entity.GroupBuySuspensionStatus;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuySuspensionRequestRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.time.LocalDateTime;
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
class AdminGroupBuyControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private GroupBuyRepository groupBuyRepository;

    @Autowired
    private GroupBuySuspensionRequestRepository groupBuySuspensionRequestRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Member saveMember(String email, Role role) {
        Member member = memberRepository.save(Member.signUp(email, passwordEncoder.encode("Pass1234!"), "홍길동"));
        ReflectionTestUtils.setField(member, "role", role);
        return memberRepository.save(member);
    }

    private GroupBuy saveOngoingGroupBuy(Long producerId) {
        GroupBuy groupBuy = groupBuyRepository.save(GroupBuy.create(1L, producerId, "산지 직송 유기농 토마토",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7), 100, 1_000));
        groupBuy.start();
        return groupBuyRepository.save(groupBuy);
    }

    private GroupBuySuspensionRequest savePendingRequest(Long groupBuyId, Long requesterId) {
        return groupBuySuspensionRequestRepository.save(
                GroupBuySuspensionRequest.request(groupBuyId, requesterId, "품절로 인한 판매 중단"));
    }

    private UsernamePasswordAuthenticationToken authOf(Member member) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
    }

    // ADMIN이 PENDING 상태의 판매정지 요청을 승인하면 204를 반환하고 대상 공동구매가 suspended=true로 바뀌는지 검증
    @Test
    void 관리자가_판매정지_요청_승인에_성공한다() throws Exception {
        Member admin = saveMember("admin-approve-suspension@example.com", Role.ADMIN);
        Member producer = saveMember("producer-approve-suspension@example.com", Role.SELLER);
        GroupBuy groupBuy = saveOngoingGroupBuy(producer.getId());
        GroupBuySuspensionRequest request = savePendingRequest(groupBuy.getId(), producer.getId());

        mockMvc.perform(post("/api/admin/groupBuys/suspension-requests/{id}/approve", request.getId())
                        .with(authentication(authOf(admin))))
                .andExpect(status().isNoContent())
                .andDo(document("admin/groupbuy-suspension-approve-success"));

        GroupBuy updated = groupBuyRepository.findById(groupBuy.getId()).orElseThrow();
        assertThat(updated.isSuspended()).isTrue();
    }

    // ADMIN이 PENDING 상태의 판매정지 요청을 반려하면 204를 반환하고 대상 공동구매는 suspended=false로 유지되는지 검증
    @Test
    void 관리자가_판매정지_요청_반려에_성공한다() throws Exception {
        Member admin = saveMember("admin-reject-suspension@example.com", Role.ADMIN);
        Member producer = saveMember("producer-reject-suspension@example.com", Role.SELLER);
        GroupBuy groupBuy = saveOngoingGroupBuy(producer.getId());
        GroupBuySuspensionRequest request = savePendingRequest(groupBuy.getId(), producer.getId());

        mockMvc.perform(post("/api/admin/groupBuys/suspension-requests/{id}/reject", request.getId())
                        .with(authentication(authOf(admin))))
                .andExpect(status().isNoContent())
                .andDo(document("admin/groupbuy-suspension-reject-success"));

        GroupBuy updated = groupBuyRepository.findById(groupBuy.getId()).orElseThrow();
        assertThat(updated.isSuspended()).isFalse();
    }

    // ADMIN이 아닌 회원이 승인 API를 호출하면 403을 반환하는지 검증
    @Test
    void 관리자가_아니면_판매정지_승인에_실패한다() throws Exception {
        Member seller = saveMember("seller-forbidden-suspension@example.com", Role.SELLER);
        GroupBuy groupBuy = saveOngoingGroupBuy(seller.getId());
        GroupBuySuspensionRequest request = savePendingRequest(groupBuy.getId(), seller.getId());

        mockMvc.perform(post("/api/admin/groupBuys/suspension-requests/{id}/approve", request.getId())
                        .with(authentication(authOf(seller))))
                .andExpect(status().isForbidden());
    }

    // 존재하지 않는 요청 ID로 승인 시도 시 404와 GROUPBUY_404_SUSPENSION_NOT_FOUND를 반환하는지 검증
    @Test
    void 존재하지_않는_판매정지_요청은_승인에_실패한다() throws Exception {
        Member admin = saveMember("admin-suspension-not-found@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/admin/groupBuys/suspension-requests/{id}/approve", 999_999L)
                        .with(authentication(authOf(admin))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUPBUY_404_SUSPENSION_NOT_FOUND"));
    }

    // 이미 승인 처리된 요청을 다시 승인 시도하면 409와 GROUPBUY_409_SUSPENSION_ALREADY_PROCESSED를 반환하는지 검증
    @Test
    void 이미_처리된_판매정지_요청은_승인에_실패한다() throws Exception {
        Member admin = saveMember("admin-suspension-already-processed@example.com", Role.ADMIN);
        Member producer = saveMember("producer-suspension-already-processed@example.com", Role.SELLER);
        GroupBuy groupBuy = saveOngoingGroupBuy(producer.getId());
        GroupBuySuspensionRequest request = savePendingRequest(groupBuy.getId(), producer.getId());
        request.approve();
        groupBuySuspensionRequestRepository.save(request);

        mockMvc.perform(post("/api/admin/groupBuys/suspension-requests/{id}/approve", request.getId())
                        .with(authentication(authOf(admin))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUPBUY_409_SUSPENSION_ALREADY_PROCESSED"));
    }

    // 상태별 판매정지 요청 목록 조회가 PENDING 요청만 PagedModel 형태로 반환하는지 검증
    @Test
    void 관리자가_PENDING_판매정지_요청_목록_조회에_성공한다() throws Exception {
        Member admin = saveMember("admin-suspension-list@example.com", Role.ADMIN);
        Member producer = saveMember("producer-suspension-list@example.com", Role.SELLER);
        GroupBuy groupBuy = saveOngoingGroupBuy(producer.getId());
        savePendingRequest(groupBuy.getId(), producer.getId());

        mockMvc.perform(get("/api/admin/groupBuys/suspension-requests").param("status", "PENDING")
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].groupBuyTitle").value("산지 직송 유기농 토마토"))
                .andDo(document("admin/groupbuy-suspension-list-success",
                        responseFields(
                                fieldWithPath("content[].id").description("판매정지 요청 ID"),
                                fieldWithPath("content[].groupBuyId").description("공동구매 ID"),
                                fieldWithPath("content[].groupBuyTitle").description("공동구매 제목"),
                                fieldWithPath("content[].requesterId").description("요청자(생산자) ID"),
                                fieldWithPath("content[].reason").description("요청 사유").optional(),
                                fieldWithPath("content[].status").description("처리 상태"),
                                fieldWithPath("content[].requestedAt").description("요청 일시"),
                                fieldWithPath("content[].decidedAt").description("처리 일시").optional(),
                                fieldWithPath("page.size").description("페이지 크기"),
                                fieldWithPath("page.number").description("페이지 번호(0부터 시작)"),
                                fieldWithPath("page.totalElements").description("전체 개수"),
                                fieldWithPath("page.totalPages").description("전체 페이지 수"))));
    }

    // 관리자가 전체 공동구매 목록을 조회할 수 있는지 검증
    @Test
    void 관리자가_전체_공동구매_목록_조회에_성공한다() throws Exception {
        Member admin = saveMember("admin-groupbuy-list@example.com", Role.ADMIN);
        Member producer = saveMember("producer-groupbuy-list@example.com", Role.SELLER);
        saveOngoingGroupBuy(producer.getId());

        mockMvc.perform(get("/api/admin/groupBuys")
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
