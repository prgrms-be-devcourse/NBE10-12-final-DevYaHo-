package com.wellbuying.domain.order.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.order.entity.Order;
import com.wellbuying.domain.order.repository.OrderRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.payment.entity.Payment;
import com.wellbuying.domain.payment.repository.PaymentRepository;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class OrderControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ProductCategoryRepository productCategoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private GroupBuyRepository groupBuyRepository;
    @Autowired
    private GroupBuyPartRepository groupBuyPartRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private OrderRepository orderRepository;

    private UsernamePasswordAuthenticationToken authOf(Member member) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
    }

    // 구매자 1명분의 성사 → 결제 승인 → 주문(PAID) 상태를 통째로 만들어 둔다
    private Order savePaidOrder(Member buyer, String productName, int quantity, int unitPrice) {
        Member seller = memberRepository.save(Member.signUp("seller-" + System.nanoTime() + "@test.com", "pw", "생산자"));
        ProductCategory category = productCategoryRepository.save(
                ProductCategory.create(null, "식품-" + System.nanoTime(), 0));
        Product product = productRepository.save(
                Product.register(seller.getId(), category.getId(), productName, "설명", unitPrice, "https://cdn/x.jpg"));
        GroupBuy groupBuy = groupBuyRepository.save(GroupBuy.create(product.getId(), seller.getId(), productName + " 공동구매",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), 1, 100));

        GroupBuyPart part = GroupBuyPart.confirm(groupBuy.getId(), buyer.getId(), quantity);
        part.applyFinalPrice(unitPrice);
        part = groupBuyPartRepository.save(part);

        int total = unitPrice * quantity;
        Payment payment = Payment.ready(part.getId(), buyer.getId(), total, "TOSS",
                "GroupBuyCompleted:" + part.getId());
        payment.approve("toss-tx-" + part.getId(), LocalDateTime.now().minusDays(1));
        payment = paymentRepository.save(payment);

        Order order = Order.pending(payment.getId(), part.getId(), buyer.getId(), "서울시 강남구 테헤란로 123", total);
        order.markPaid();
        return orderRepository.save(order);
    }

    @Test
    void 내_결제_내역_목록은_상품명_수량_총결제금액을_담아_최신순으로_반환한다() throws Exception {
        Member buyer = memberRepository.save(Member.signUp("buyer-" + System.nanoTime() + "@test.com", "pw", "구매자"));
        savePaidOrder(buyer, "청송 사과 5kg", 2, 12_000);
        savePaidOrder(buyer, "완도 전복 1kg", 1, 40_000);

        mockMvc.perform(get("/api/orders/me").with(authentication(authOf(buyer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].productName").value("완도 전복 1kg"))
                .andExpect(jsonPath("$.content[0].quantity").value(1))
                .andExpect(jsonPath("$.content[0].totalPrice").value(40_000))
                .andExpect(jsonPath("$.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.content[1].productName").value("청송 사과 5kg"))
                .andExpect(jsonPath("$.content[1].totalPrice").value(24_000));
    }

    @Test
    void 결제_상세는_단가와_결제수단_배송지를_포함한다() throws Exception {
        Member buyer = memberRepository.save(Member.signUp("buyer-" + System.nanoTime() + "@test.com", "pw", "구매자"));
        Order order = savePaidOrder(buyer, "청송 사과 5kg", 3, 10_000);

        mockMvc.perform(get("/api/orders/me/{orderId}", order.getOrderId()).with(authentication(authOf(buyer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order.getOrderId()))
                .andExpect(jsonPath("$.productName").value("청송 사과 5kg"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.unitPrice").value(10_000))
                .andExpect(jsonPath("$.totalPrice").value(30_000))
                .andExpect(jsonPath("$.shippingAddress").value("서울시 강남구 테헤란로 123"))
                .andExpect(jsonPath("$.pgProvider").value("TOSS"))
                .andExpect(jsonPath("$.paymentStatus").value("APPROVED"));
    }

    @Test
    void 남의_주문_상세_조회는_404() throws Exception {
        Member owner = memberRepository.save(Member.signUp("owner-" + System.nanoTime() + "@test.com", "pw", "주인"));
        Member other = memberRepository.save(Member.signUp("other-" + System.nanoTime() + "@test.com", "pw", "타인"));
        Order order = savePaidOrder(owner, "청송 사과 5kg", 1, 10_000);

        mockMvc.perform(get("/api/orders/me/{orderId}", order.getOrderId()).with(authentication(authOf(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_404_NOT_FOUND"));
    }

    @Test
    void 인증_없이_결제_내역_조회는_401() throws Exception {
        mockMvc.perform(get("/api/orders/me"))
                .andExpect(status().isUnauthorized());
    }
}
