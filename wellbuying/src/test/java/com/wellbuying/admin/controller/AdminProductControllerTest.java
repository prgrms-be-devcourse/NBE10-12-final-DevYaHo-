package com.wellbuying.admin.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.entity.ProductStatus;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
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
class AdminProductControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Member saveMember(String email, Role role) {
        Member member = memberRepository.save(Member.signUp(email, passwordEncoder.encode("Pass1234!"), "홍길동"));
        ReflectionTestUtils.setField(member, "role", role);
        return memberRepository.save(member);
    }

    private Product savePendingProduct(Long sellerId) {
        ProductCategory category = productCategoryRepository.save(ProductCategory.create(null, "테스트카테고리"));
        return productRepository.save(Product.register(sellerId, category.getId(), "테스트상품", "설명", 10000, "url"));
    }

    private UsernamePasswordAuthenticationToken authOf(Member member) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
    }

    // ADMIN이 PENDING 상태의 상품을 승인하면 204를 반환하고 status가 APPROVED로 바뀌는지 검증
    @Test
    void 관리자가_상품_승인에_성공한다() throws Exception {
        Member admin = saveMember("admin-product-approve@example.com", Role.ADMIN);
        Member seller = saveMember("seller-product-approve@example.com", Role.SELLER);
        Product product = savePendingProduct(seller.getId());

        mockMvc.perform(post("/api/admin/products/{productId}/approve", product.getId())
                        .with(authentication(authOf(admin))))
                .andExpect(status().isNoContent())
                .andDo(document("admin/product-approve-success"));

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getStatus()).isEqualTo(ProductStatus.APPROVED);
    }

    // ADMIN이 PENDING 상태의 상품을 거절하면 204를 반환하고 status가 REJECTED로 바뀌는지 검증
    @Test
    void 관리자가_상품_거절에_성공한다() throws Exception {
        Member admin = saveMember("admin-product-reject@example.com", Role.ADMIN);
        Member seller = saveMember("seller-product-reject@example.com", Role.SELLER);
        Product product = savePendingProduct(seller.getId());

        mockMvc.perform(post("/api/admin/products/{productId}/reject", product.getId())
                        .with(authentication(authOf(admin))))
                .andExpect(status().isNoContent())
                .andDo(document("admin/product-reject-success"));

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getStatus()).isEqualTo(ProductStatus.REJECTED);
    }

    // ADMIN이 아닌 회원이 승인 API를 호출하면 403을 반환하는지 검증
    @Test
    void 관리자가_아니면_상품_승인에_실패한다() throws Exception {
        Member seller = saveMember("seller-product-forbidden@example.com", Role.SELLER);
        Product product = savePendingProduct(seller.getId());

        mockMvc.perform(post("/api/admin/products/{productId}/approve", product.getId())
                        .with(authentication(authOf(seller))))
                .andExpect(status().isForbidden());
    }

    // 인증 정보 없이 승인 API를 호출하면 401을 반환하는지 검증
    @Test
    void 인증되지_않은_요청은_상품_승인에_실패한다() throws Exception {
        Member seller = saveMember("seller-product-unauth@example.com", Role.SELLER);
        Product product = savePendingProduct(seller.getId());

        mockMvc.perform(post("/api/admin/products/{productId}/approve", product.getId()))
                .andExpect(status().isUnauthorized());
    }

    // 존재하지 않는 productId로 승인 시도 시 404와 PRODUCT_404_NOT_FOUND를 반환하는지 검증
    @Test
    void 존재하지_않는_상품은_승인에_실패한다() throws Exception {
        Member admin = saveMember("admin-product-not-found@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/admin/products/{productId}/approve", 999_999L)
                        .with(authentication(authOf(admin))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_404_NOT_FOUND"))
                .andDo(document("admin/product-approve-not-found",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 이미 처리된(APPROVED) 상품을 다시 승인 시도하면 409와 PRODUCT_409_ALREADY_PROCESSED를 반환하는지 검증
    @Test
    void 이미_처리된_상품은_승인에_실패한다() throws Exception {
        Member admin = saveMember("admin-product-already-processed@example.com", Role.ADMIN);
        Member seller = saveMember("seller-product-already-processed@example.com", Role.SELLER);
        Product product = savePendingProduct(seller.getId());
        product.approve();
        productRepository.save(product);

        mockMvc.perform(post("/api/admin/products/{productId}/approve", product.getId())
                        .with(authentication(authOf(admin))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_409_ALREADY_PROCESSED"));
    }
}
