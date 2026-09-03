package com.wellbuying.domain.product.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ProductControllerSecurityTest extends AbstractIntegrationTest {

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

    private ProductCategory saveCategory() {
        return productCategoryRepository.save(ProductCategory.create(null, "테스트카테고리"));
    }

    private UsernamePasswordAuthenticationToken authOf(Member member) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
    }

    // BUYER가 상품 등록 시도 시 서비스에서 DB role을 재확인하고 403과 PRODUCT_403_SELLER_ONLY를 반환하는지 검증
    @Test
    void BUYER가_상품_등록_시도시_403과_PRODUCT_403_SELLER_ONLY를_반환한다() throws Exception {
        Member buyer = saveMember("buyer-product-security@example.com", Role.BUYER);
        ProductCategory category = saveCategory();
        String body = """
                {"categoryId":%d,"productName":"테스트상품","startPrice":10000}
                """.formatted(category.getId());

        mockMvc.perform(post("/api/products")
                        .with(authentication(authOf(buyer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_403_SELLER_ONLY"));
    }

    // SELLER가 유효한 categoryId로 상품 등록 시 201과 Location 헤더를 반환하는지 검증
    @Test
    void SELLER가_상품_등록시_201과_Location_헤더를_반환한다() throws Exception {
        Member seller = saveMember("seller-product-security@example.com", Role.SELLER);
        ProductCategory category = saveCategory();
        String body = """
                {"categoryId":%d,"productName":"테스트상품","startPrice":10000}
                """.formatted(category.getId());

        mockMvc.perform(post("/api/products")
                        .with(authentication(authOf(seller)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    // 인증 없이 GET /api/products/mine 호출 시 401을 반환하는지 검증 — SecurityConfig 순서 버그 회귀 방지
    @Test
    void 인증_없이_내_상품_조회시_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/products/mine"))
                .andExpect(status().isUnauthorized());
    }

    // 존재하지 않는 categoryId로 상품 등록 시 404와 PRODUCT_404_CATEGORY_NOT_FOUND를 반환하는지 검증
    @Test
    void 존재하지_않는_카테고리로_상품_등록시_404를_반환한다() throws Exception {
        Member seller = saveMember("seller-no-category@example.com", Role.SELLER);
        String body = """
                {"categoryId":999999,"productName":"테스트상품","startPrice":10000}
                """;

        mockMvc.perform(post("/api/products")
                        .with(authentication(authOf(seller)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_404_CATEGORY_NOT_FOUND"));
    }

    // SELLER의 상품이 GET /api/products/mine에서 상태(APPROVED/PENDING)와 무관하게 모두 포함되는지 검증
    @Test
    void SELLER의_상태무관_상품_전체가_내_상품_조회에_반환된다() throws Exception {
        Member seller = saveMember("seller-mine-security@example.com", Role.SELLER);
        ProductCategory category = saveCategory();

        Product approved = Product.register(seller.getId(), category.getId(), "승인된상품", null, 10000, null);
        approved.approve();
        productRepository.save(approved);
        productRepository.save(
                Product.register(seller.getId(), category.getId(), "대기중상품", null, 20000, null));

        mockMvc.perform(get("/api/products/mine")
                        .with(authentication(authOf(seller))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }
}
