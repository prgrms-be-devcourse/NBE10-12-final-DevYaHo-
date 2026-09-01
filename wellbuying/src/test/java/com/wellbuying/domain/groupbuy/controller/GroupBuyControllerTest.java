package com.wellbuying.domain.groupbuy.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPrice;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
class GroupBuyControllerTest extends AbstractIntegrationTest {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private GroupBuyRepository groupBuyRepository;

    @Autowired
    private GroupBuyPriceRepository groupBuyPriceRepository;

    @Autowired
    private GroupBuyPartRepository groupBuyPartRepository;

    @Autowired
    private GroupBuyCounterRepository groupBuyCounterRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 승인/역할 상승 API가 아직 없어, 테스트에서만 회원의 role을 SELLER로 직접 세팅한다
    private Member saveSeller(String email) {
        Member member = memberRepository.save(Member.signUp(email, passwordEncoder.encode("Pass1234!"), "생산자"));
        ReflectionTestUtils.setField(member, "role", Role.SELLER);
        return memberRepository.save(member);
    }

    private Member saveBuyer(String email) {
        return memberRepository.save(Member.signUp(email, passwordEncoder.encode("Pass1234!"), "구매자"));
    }

    private UsernamePasswordAuthenticationToken authOf(Member member) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
    }

    private GroupBuy saveOngoingGroupBuy(Long producerId, int minQuantity, int maxQuantity) {
        GroupBuy groupBuy = groupBuyRepository.save(GroupBuy.create(1L, producerId, "산지 직송 유기농 토마토",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7), minQuantity, maxQuantity));
        groupBuy.start();
        groupBuyPriceRepository.save(GroupBuyPrice.of(groupBuy.getId(), 1, 1, 15_000));
        groupBuyCounterRepository.initialize(groupBuy.getId(), Duration.ofMinutes(10));
        return groupBuyRepository.save(groupBuy);
    }

    // SELLER 역할의 회원이 유효한 가격 구간과 함께 공동구매를 생성하면 201과 생성된 정보를 반환하는지 검증
    @Test
    void 셀러가_공동구매_생성에_성공한다() throws Exception {
        Member seller = saveSeller("groupbuy-create-success@example.com");
        ProductCategory category = productCategoryRepository.save(ProductCategory.create(null, "식품"));
        Product product = productRepository.save(
                Product.register(seller.getId(), category.getId(), "유기농 토마토", null, 15_000, null));
        String startAt = LocalDateTime.now().plusDays(1).format(FORMATTER);
        String endAt = LocalDateTime.now().plusDays(8).format(FORMATTER);
        String requestBody = """
                {
                  "productId": %d,
                  "title": "산지 직송 유기농 토마토",
                  "startAt": "%s",
                  "endAt": "%s",
                  "minQuantity": 100,
                  "maxQuantity": 10000,
                  "priceTiers": [
                    {"tierOrder": 1, "thresholdQuantity": 100, "unitPrice": 15000},
                    {"tierOrder": 2, "thresholdQuantity": 1000, "unitPrice": 12000}
                  ]
                }
                """.formatted(product.getId(), startAt, endAt);

        mockMvc.perform(post("/api/groupBuys")
                        .with(authentication(authOf(seller)))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("산지 직송 유기농 토마토"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.priceTiers.length()").value(2))
                .andDo(document("groupbuy/create-success",
                        requestFields(
                                fieldWithPath("productId").description("상품 ID"),
                                fieldWithPath("title").description("공동구매 제목"),
                                fieldWithPath("startAt").description("시작 일시"),
                                fieldWithPath("endAt").description("마감 일시"),
                                fieldWithPath("minQuantity").description("성사 최소 수량"),
                                fieldWithPath("maxQuantity").description("최대(재고) 수량"),
                                fieldWithPath("priceTiers[].tierOrder").description("구간 순서"),
                                fieldWithPath("priceTiers[].thresholdQuantity").description("구간 적용 기준 수량"),
                                fieldWithPath("priceTiers[].unitPrice").description("구간 판매 단가"))));
    }

    // BUYER 역할의 회원이 공동구매 생성을 시도하면 403과 GROUPBUY_403_FORBIDDEN을 반환하는지 검증
    @Test
    void 셀러가_아니면_공동구매_생성에_실패한다() throws Exception {
        Member buyer = saveBuyer("groupbuy-create-forbidden@example.com");
        String startAt = LocalDateTime.now().plusDays(1).format(FORMATTER);
        String endAt = LocalDateTime.now().plusDays(8).format(FORMATTER);
        String requestBody = """
                {
                  "productId": 1,
                  "title": "제목",
                  "startAt": "%s",
                  "endAt": "%s",
                  "minQuantity": 100,
                  "maxQuantity": 10000,
                  "priceTiers": [{"tierOrder": 1, "thresholdQuantity": 100, "unitPrice": 15000}]
                }
                """.formatted(startAt, endAt);

        mockMvc.perform(post("/api/groupBuys")
                        .with(authentication(authOf(buyer)))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUPBUY_403_FORBIDDEN"))
                .andDo(document("groupbuy/create-forbidden",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 인증 정보 없이 공동구매 생성을 시도하면 401과 AUTH_401_REQUIRED를 반환하는지 검증
    @Test
    void 인증되지_않은_요청은_공동구매_생성에_실패한다() throws Exception {
        String requestBody = """
                {
                  "productId": 1,
                  "title": "제목",
                  "startAt": "2099-01-01T00:00:00",
                  "endAt": "2099-01-08T00:00:00",
                  "minQuantity": 100,
                  "maxQuantity": 10000,
                  "priceTiers": [{"tierOrder": 1, "thresholdQuantity": 100, "unitPrice": 15000}]
                }
                """;

        mockMvc.perform(post("/api/groupBuys")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_REQUIRED"));
    }

    // 상세 조회는 인증 없이도 가격 구간을 포함한 공동구매 정보를 반환하는지 검증
    @Test
    void 상세_조회는_인증_없이도_성공한다() throws Exception {
        Member seller = saveSeller("groupbuy-detail@example.com");
        GroupBuy groupBuy = saveOngoingGroupBuy(seller.getId(), 100, 10_000);

        mockMvc.perform(get("/api/groupBuys/{id}", groupBuy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupBuy.getId()))
                .andExpect(jsonPath("$.status").value("ONGOING"))
                .andExpect(jsonPath("$.priceTiers.length()").value(1))
                .andDo(document("groupbuy/detail-success",
                        responseFields(
                                fieldWithPath("id").description("공동구매 ID"),
                                fieldWithPath("productId").description("상품 ID"),
                                fieldWithPath("productName").description("상품명"),
                                fieldWithPath("productCategory").description("상품 카테고리"),
                                fieldWithPath("producerId").description("생산자 ID"),
                                fieldWithPath("title").description("제목"),
                                fieldWithPath("status").description("상태"),
                                fieldWithPath("startAt").description("시작 일시"),
                                fieldWithPath("endAt").description("마감 일시"),
                                fieldWithPath("minQuantity").description("최소 수량"),
                                fieldWithPath("maxQuantity").description("최대 수량"),
                                fieldWithPath("priceTiers[].tierOrder").description("구간 순서"),
                                fieldWithPath("priceTiers[].thresholdQuantity").description("구간 기준 수량"),
                                fieldWithPath("priceTiers[].unitPrice").description("구간 단가"),
                                fieldWithPath("createdAt").description("생성 일시"),
                                fieldWithPath("suspended").description("판매정지 여부"))));
    }

    // 목록 조회 응답이 PagedModel 형태(content + page 메타데이터)로 직렬화되는지 검증
    // (Page를 그대로 직렬화하면 JSON 구조가 안정적이지 않다는 경고가 있어 VIA_DTO로 고정했다)
    @Test
    void 목록_조회는_PagedModel_형태로_응답한다() throws Exception {
        Member seller = saveSeller("groupbuy-list@example.com");
        saveOngoingGroupBuy(seller.getId(), 100, 10_000);

        mockMvc.perform(get("/api/groupBuys").param("status", "ONGOING").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andDo(document("groupbuy/list-success",
                        responseFields(
                                fieldWithPath("content[].id").description("공동구매 ID"),
                                fieldWithPath("content[].productId").description("상품 ID"),
                                fieldWithPath("content[].productName").description("상품명"),
                                fieldWithPath("content[].productCategory").description("상품 카테고리"),
                                fieldWithPath("content[].producerId").description("생산자 ID"),
                                fieldWithPath("content[].title").description("제목"),
                                fieldWithPath("content[].status").description("상태"),
                                fieldWithPath("content[].startAt").description("시작 일시"),
                                fieldWithPath("content[].endAt").description("마감 일시"),
                                fieldWithPath("content[].currentQuantity").description("현재 누적 참여 수량"),
                                fieldWithPath("content[].maxQuantity").description("최대 수량"),
                                fieldWithPath("content[].suspended").description("판매정지 여부"),
                                fieldWithPath("page.size").description("페이지 크기"),
                                fieldWithPath("page.number").description("페이지 번호(0부터 시작)"),
                                fieldWithPath("page.totalElements").description("전체 요소 수"),
                                fieldWithPath("page.totalPages").description("전체 페이지 수"))));
    }

    // 생산자 본인의 공동구매 목록만 반환하는지 검증 (다른 생산자 것은 제외)
    @Test
    void 내_공동구매_목록_조회는_본인_소유만_반환한다() throws Exception {
        Member seller = saveSeller("groupbuy-mine-seller@example.com");
        Member otherSeller = saveSeller("groupbuy-mine-other-seller@example.com");
        saveOngoingGroupBuy(seller.getId(), 100, 10_000);
        saveOngoingGroupBuy(otherSeller.getId(), 100, 10_000);

        mockMvc.perform(get("/api/groupBuys/mine").with(authentication(authOf(seller))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].producerId").value(seller.getId()));
    }

    // 인증 없이 내 공동구매 목록을 조회하면 401을 반환하는지 검증
    @Test
    void 인증_없이_내_공동구매_목록_조회는_실패한다() throws Exception {
        mockMvc.perform(get("/api/groupBuys/mine"))
                .andExpect(status().isUnauthorized());
    }

    // 실시간 상태 조회가 참여자 수/잔여 수량/남은 시간을 반환하는지 검증
    @Test
    void 실시간_상태_조회에_성공한다() throws Exception {
        Member seller = saveSeller("groupbuy-status@example.com");
        Member buyer = saveBuyer("groupbuy-status-buyer@example.com");
        GroupBuy groupBuy = saveOngoingGroupBuy(seller.getId(), 100, 1_000);
        groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuy.getId(), buyer.getId(), 200));
        groupBuy.increaseQuantity(200);
        groupBuyRepository.save(groupBuy);

        mockMvc.perform(get("/api/groupBuys/{id}/status", groupBuy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentQuantity").value(200))
                .andExpect(jsonPath("$.remainingQuantity").value(800))
                .andExpect(jsonPath("$.participantCount").value(1))
                .andDo(document("groupbuy/status-success",
                        responseFields(
                                fieldWithPath("id").description("공동구매 ID"),
                                fieldWithPath("status").description("상태"),
                                fieldWithPath("currentQuantity").description("현재 누적 참여 수량"),
                                fieldWithPath("remainingQuantity").description("잔여 수량"),
                                fieldWithPath("participantCount").description("참여자 수"),
                                fieldWithPath("remainingSeconds").description("남은 시간(초)"))));
    }

    // 재고가 남아 아직 성사되지 않은 상태에서 참여 신청은 201을 반환하되, 가격은 성사 전이라 아직 null인지 검증
    // (예상가는 프론트가 GET /price + GET /status로 직접 계산해 보여주고, 백엔드는 계산/저장하지 않는다)
    @Test
    void 재고가_남아있으면_참여_신청은_성공하지만_가격은_아직_null이다() throws Exception {
        Member seller = saveSeller("groupbuy-part-success-seller@example.com");
        Member buyer = saveBuyer("groupbuy-part-success-buyer@example.com");
        GroupBuy groupBuy = saveOngoingGroupBuy(seller.getId(), 100, 10_000);

        mockMvc.perform(post("/api/groupBuys/{id}/part", groupBuy.getId())
                        .with(authentication(authOf(buyer)))
                        .contentType("application/json")
                        .content("{\"quantity\": 50, \"address\": \"서울특별시 강남구 테헤란로 123\", "
                                + "\"addressDetail\": \"4층\", \"zipcode\": \"06234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(50))
                .andExpect(jsonPath("$.appliedPrice").value(nullValue()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andDo(document("groupbuy/part-create-success",
                        requestFields(
                                fieldWithPath("quantity").description("참여 수량"),
                                fieldWithPath("address").description("배송지 주소"),
                                fieldWithPath("addressDetail").description("배송지 상세주소").optional(),
                                fieldWithPath("zipcode").description("우편번호")),
                        responseFields(
                                fieldWithPath("id").description("참여 ID"),
                                fieldWithPath("groupBuyId").description("공동구매 ID"),
                                fieldWithPath("quantity").description("참여 수량"),
                                fieldWithPath("appliedPrice").description("확정 단가 (공동구매 성사 전에는 null)").optional(),
                                fieldWithPath("status").description("참여 상태"),
                                fieldWithPath("address").description("배송지 주소"),
                                fieldWithPath("addressDetail").description("배송지 상세주소").optional(),
                                fieldWithPath("zipcode").description("우편번호"),
                                fieldWithPath("createdAt").description("참여 일시"))));

        mockMvc.perform(get("/api/groupBuys/{id}/part/me", groupBuy.getId())
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participated").value(true))
                .andExpect(jsonPath("$.part.quantity").value(50))
                .andExpect(jsonPath("$.part.appliedPrice").value(nullValue()));
    }

    // 매진으로 공동구매가 성사되면, 먼저 참여해 더 비싼 구간에 있었던 참여자에게도 최종(가장 낮은) 구간 단가가
    // 실제 DB/HTTP 응답까지 소급 적용되는지 end-to-end로 검증
    @Test
    void 매진으로_성사되면_먼저_참여한_사람에게도_최종가가_소급_적용된다() throws Exception {
        Member seller = saveSeller("groupbuy-final-price-seller@example.com");
        Member earlyBuyer = saveBuyer("groupbuy-final-price-early@example.com");
        Member lastBuyer = saveBuyer("groupbuy-final-price-last@example.com");
        GroupBuy groupBuy = groupBuyRepository.save(GroupBuy.create(1L, seller.getId(), "제목",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7), 10, 100));
        groupBuy.start();
        groupBuyRepository.save(groupBuy);
        groupBuyPriceRepository.save(GroupBuyPrice.of(groupBuy.getId(), 1, 1, 15_000));
        groupBuyPriceRepository.save(GroupBuyPrice.of(groupBuy.getId(), 2, 100, 10_000));
        groupBuyCounterRepository.initialize(groupBuy.getId(), Duration.ofMinutes(10));

        // 50개 시점(15,000원 구간)에 먼저 참여
        mockMvc.perform(post("/api/groupBuys/{id}/part", groupBuy.getId())
                        .with(authentication(authOf(earlyBuyer)))
                        .contentType("application/json")
                        .content("{\"quantity\": 50, \"address\": \"서울특별시 강남구 테헤란로 123\", \"zipcode\": \"06234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appliedPrice").value(nullValue()));

        // 나머지 50개가 채워져 100개(10,000원 구간)로 매진 - 이 참여로 즉시 성사된다
        mockMvc.perform(post("/api/groupBuys/{id}/part", groupBuy.getId())
                        .with(authentication(authOf(lastBuyer)))
                        .contentType("application/json")
                        .content("{\"quantity\": 50, \"address\": \"서울특별시 강남구 테헤란로 123\", \"zipcode\": \"06234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appliedPrice").value(10_000));

        // 먼저 참여했던 사람도 최종가(10,000원)로 소급 적용됐는지 확인
        mockMvc.perform(get("/api/groupBuys/{id}/part/me", groupBuy.getId())
                        .with(authentication(authOf(earlyBuyer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.part.appliedPrice").value(10_000));
        mockMvc.perform(get("/api/groupBuys/{id}/status", groupBuy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    // 잔여 수량을 초과하는 참여 신청은 409와 GROUPBUY_409_SOLD_OUT을 반환하는지 검증
    @Test
    void 재고를_초과하면_참여_신청에_실패한다() throws Exception {
        Member seller = saveSeller("groupbuy-soldout-seller@example.com");
        Member buyer = saveBuyer("groupbuy-soldout-buyer@example.com");
        GroupBuy groupBuy = saveOngoingGroupBuy(seller.getId(), 10, 100);

        mockMvc.perform(post("/api/groupBuys/{id}/part", groupBuy.getId())
                        .with(authentication(authOf(buyer)))
                        .contentType("application/json")
                        .content("{\"quantity\": 200, \"address\": \"서울특별시 강남구 테헤란로 123\", \"zipcode\": \"06234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUPBUY_409_SOLD_OUT"))
                .andDo(document("groupbuy/part-create-sold-out",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 참여자 본인이 참여를 취소하면 204를 반환하고, 이후 다시 확인하면 참여 내역이 없어지는지 검증
    @Test
    void 참여_취소에_성공한다() throws Exception {
        Member seller = saveSeller("groupbuy-part-cancel-seller@example.com");
        Member buyer = saveBuyer("groupbuy-part-cancel-buyer@example.com");
        GroupBuy groupBuy = saveOngoingGroupBuy(seller.getId(), 100, 10_000);
        GroupBuyPart part = groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuy.getId(), buyer.getId(), 50));
        groupBuy.increaseQuantity(50);
        groupBuyRepository.save(groupBuy);
        groupBuyCounterRepository.tryIncrease(groupBuy.getId(), 50, groupBuy.getMaxQuantity());

        mockMvc.perform(delete("/api/groupBuys/{id}/part/{partId}", groupBuy.getId(), part.getId())
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isNoContent())
                .andDo(document("groupbuy/part-cancel-success"));

        mockMvc.perform(get("/api/groupBuys/{id}/part/me", groupBuy.getId())
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participated").value(false));
    }

    // 생산자 본인이 시작 전(READY) 공동구매를 취소하면 204를 반환하는지 검증
    @Test
    void 생산자가_시작_전_공동구매_취소에_성공한다() throws Exception {
        Member seller = saveSeller("groupbuy-cancel-seller@example.com");
        GroupBuy groupBuy = groupBuyRepository.save(GroupBuy.create(1L, seller.getId(), "제목",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8), 100, 10_000));

        mockMvc.perform(delete("/api/groupBuys/{id}", groupBuy.getId())
                        .with(authentication(authOf(seller))))
                .andExpect(status().isNoContent())
                .andDo(document("groupbuy/cancel-success"));

        mockMvc.perform(get("/api/groupBuys/{id}/status", groupBuy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    // 생산자 본인의 ONGOING 공동구매에 판매정지를 요청하면 201을 반환하는지 검증
    @Test
    void 생산자가_판매정지_요청에_성공한다() throws Exception {
        Member seller = saveSeller("groupbuy-suspension-request-seller@example.com");
        GroupBuy groupBuy = saveOngoingGroupBuy(seller.getId(), 100, 10_000);

        mockMvc.perform(post("/api/groupBuys/{id}/suspension-requests", groupBuy.getId())
                        .with(authentication(authOf(seller)))
                        .contentType("application/json")
                        .content("{\"reason\": \"품절로 인한 판매 중단\"}"))
                .andExpect(status().isCreated())
                .andDo(document("groupbuy/suspension-request-success"));
    }

    // 본인 소유가 아닌 공동구매에 판매정지를 요청하면 403을 반환하는지 검증
    @Test
    void 소유자가_아니면_판매정지_요청에_실패한다() throws Exception {
        Member seller = saveSeller("groupbuy-suspension-owner-seller@example.com");
        Member otherSeller = saveSeller("groupbuy-suspension-owner-other@example.com");
        GroupBuy groupBuy = saveOngoingGroupBuy(seller.getId(), 100, 10_000);

        mockMvc.perform(post("/api/groupBuys/{id}/suspension-requests", groupBuy.getId())
                        .with(authentication(authOf(otherSeller)))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUPBUY_403_FORBIDDEN"));
    }

    // 이미 처리 대기 중인 판매정지 요청이 있으면 409와 GROUPBUY_409_SUSPENSION_ALREADY_REQUESTED를 반환하는지 검증
    @Test
    void 이미_요청된_판매정지는_중복_요청에_실패한다() throws Exception {
        Member seller = saveSeller("groupbuy-suspension-duplicate-seller@example.com");
        GroupBuy groupBuy = saveOngoingGroupBuy(seller.getId(), 100, 10_000);

        mockMvc.perform(post("/api/groupBuys/{id}/suspension-requests", groupBuy.getId())
                        .with(authentication(authOf(seller)))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/groupBuys/{id}/suspension-requests", groupBuy.getId())
                        .with(authentication(authOf(seller)))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUPBUY_409_SUSPENSION_ALREADY_REQUESTED"));
    }

    // 판매정지된 공동구매는 참여 신청이 차단되는지 검증
    @Test
    void 판매정지된_공동구매는_참여_신청이_차단된다() throws Exception {
        Member seller = saveSeller("groupbuy-suspended-participate-seller@example.com");
        Member buyer = saveBuyer("groupbuy-suspended-participate-buyer@example.com");
        GroupBuy groupBuy = saveOngoingGroupBuy(seller.getId(), 100, 10_000);
        groupBuy.suspend();
        groupBuyRepository.save(groupBuy);

        mockMvc.perform(post("/api/groupBuys/{id}/part", groupBuy.getId())
                        .with(authentication(authOf(buyer)))
                        .contentType("application/json")
                        .content("{\"quantity\": 50, \"address\": \"서울특별시 강남구 테헤란로 123\", \"zipcode\": \"06234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUPBUY_409_SUSPENDED"));
    }
}
