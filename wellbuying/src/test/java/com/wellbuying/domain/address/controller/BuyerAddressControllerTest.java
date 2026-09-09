package com.wellbuying.domain.address.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.address.entity.BuyerAddress;
import com.wellbuying.domain.address.repository.BuyerAddressRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class BuyerAddressControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BuyerAddressRepository buyerAddressRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Member saveMember(String email) {
        return memberRepository.save(Member.signUp(email, passwordEncoder.encode("Pass1234!"), "홍길동"));
    }

    private UsernamePasswordAuthenticationToken authOf(Member member) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
    }

    // 회원의 첫 배송지는 요청에서 지정하지 않아도 자동으로 기본 배송지가 되는지 검증
    @Test
    void 첫_배송지는_자동으로_기본_배송지가_된다() throws Exception {
        Member buyer = saveMember("buyer-first-address@example.com");
        String requestBody = """
                {
                  "address": "서울시 강남구",
                  "addressDetail": "101호",
                  "zipcode": "06236",
                  "isDefault": false
                }
                """;

        mockMvc.perform(post("/api/members/me/addresses")
                        .with(authentication(authOf(buyer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true))
                .andDo(document("address/create-success",
                        responseFields(
                                fieldWithPath("id").description("배송지 ID"),
                                fieldWithPath("address").description("주소"),
                                fieldWithPath("addressDetail").description("상세 주소").optional(),
                                fieldWithPath("zipcode").description("우편번호"),
                                fieldWithPath("isDefault").description("기본 배송지 여부"),
                                fieldWithPath("createdAt").description("등록 일시"))));
    }

    // 두 번째 배송지를 isDefault=true로 등록하면 기존 기본 배송지가 자동으로 해제되는지 검증
    @Test
    void 새_배송지를_기본으로_등록하면_기존_기본_배송지는_해제된다() throws Exception {
        Member buyer = saveMember("buyer-second-default@example.com");
        BuyerAddress first = buyerAddressRepository.save(
                BuyerAddress.create(buyer.getId(), "서울시 강남구", null, "06236", true));

        String requestBody = """
                {
                  "address": "서울시 서초구",
                  "zipcode": "06611",
                  "isDefault": true
                }
                """;
        mockMvc.perform(post("/api/members/me/addresses")
                        .with(authentication(authOf(buyer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true));

        BuyerAddress reloaded = buyerAddressRepository.findById(first.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.isDefault()).isFalse();
    }

    // 목록 조회 응답이 기본 배송지를 맨 앞에 두고 정렬되는지 검증
    @Test
    void 목록_조회는_기본_배송지가_맨_앞에_오도록_정렬된다() throws Exception {
        Member buyer = saveMember("buyer-list-order@example.com");
        buyerAddressRepository.save(BuyerAddress.create(buyer.getId(), "서울시 강남구", null, "06236", false));
        BuyerAddress secondDefault = buyerAddressRepository.save(
                BuyerAddress.create(buyer.getId(), "서울시 서초구", null, "06611", true));

        mockMvc.perform(get("/api/members/me/addresses")
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(secondDefault.getId()))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    // 다른 배송지를 기본으로 지정하면 기존 기본이 해제되고 지정한 배송지가 기본이 되는지 검증
    @Test
    void 기본_배송지_지정에_성공한다() throws Exception {
        Member buyer = saveMember("buyer-set-default@example.com");
        BuyerAddress first = buyerAddressRepository.save(
                BuyerAddress.create(buyer.getId(), "서울시 강남구", null, "06236", true));
        BuyerAddress second = buyerAddressRepository.save(
                BuyerAddress.create(buyer.getId(), "서울시 서초구", null, "06611", false));

        mockMvc.perform(patch("/api/members/me/addresses/{addressId}/default", second.getId())
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isNoContent())
                .andDo(document("address/set-default-success"));

        org.assertj.core.api.Assertions.assertThat(
                buyerAddressRepository.findById(first.getId()).orElseThrow().isDefault()).isFalse();
        org.assertj.core.api.Assertions.assertThat(
                buyerAddressRepository.findById(second.getId()).orElseThrow().isDefault()).isTrue();
    }

    // 다른 회원의 배송지를 기본으로 지정하려 하면 403을 반환하는지 검증
    @Test
    void 본인_소유가_아닌_배송지를_기본으로_지정하면_403을_반환한다() throws Exception {
        Member owner = saveMember("buyer-owner@example.com");
        Member other = saveMember("buyer-other@example.com");
        BuyerAddress address = buyerAddressRepository.save(
                BuyerAddress.create(owner.getId(), "서울시 강남구", null, "06236", true));

        mockMvc.perform(patch("/api/members/me/addresses/{addressId}/default", address.getId())
                        .with(authentication(authOf(other))))
                .andExpect(status().isForbidden());
    }

    // 존재하지 않는 배송지를 기본으로 지정하려 하면 404를 반환하는지 검증
    @Test
    void 존재하지_않는_배송지를_기본으로_지정하면_404를_반환한다() throws Exception {
        Member buyer = saveMember("buyer-not-found@example.com");

        mockMvc.perform(patch("/api/members/me/addresses/{addressId}/default", 999_999L)
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isNotFound());
    }
}
