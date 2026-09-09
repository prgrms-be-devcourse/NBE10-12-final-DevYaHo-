package com.wellbuying.domain.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.dto.GroupBuyCreateRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuyCreateRequest.PriceTierRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuyProductSummaryResponse;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPrice;
import com.wellbuying.domain.groupbuy.event.GroupBuyEventPublisher;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import com.wellbuying.domain.product.service.ProductService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupBuyServiceTest {

    @Mock
    private GroupBuyRepository groupBuyRepository;

    @Mock
    private GroupBuyPriceRepository groupBuyPriceRepository;

    @Mock
    private GroupBuyPartRepository groupBuyPartRepository;

    @Mock
    private GroupBuyCounterRepository groupBuyCounterRepository;

    @Mock
    private GroupBuyEventPublisher groupBuyEventPublisher;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCategoryRepository productCategoryRepository;

    @InjectMocks
    private GroupBuyService groupBuyService;

    // 검증 실패로 응답 생성까지 가지 않는 테스트도 있어(getProductName/getCategoryId 미사용) lenient로 둔다
    private Product ownedProduct() {
        Product product = mock(Product.class);
        org.mockito.Mockito.lenient().when(product.getProductName()).thenReturn("정직한 사과");
        org.mockito.Mockito.lenient().when(product.getCategoryId()).thenReturn(1L);
        return product;
    }

    private GroupBuyCreateRequest createRequest() {
        return new GroupBuyCreateRequest(10L, "정직한 사과 공동구매",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8),
                100, 10_000,
                List.of(new PriceTierRequest(1, 100, 15_000), new PriceTierRequest(2, 1_000, 12_000)));
    }

    private Member sellerMember() {
        Member member = mock(Member.class);
        when(member.getRole()).thenReturn(Role.SELLER);
        return member;
    }

    // SELLER 역할의 회원이 유효한 요청으로 공동구매를 생성하면 가격 구간 저장과 Redis 카운터 초기화까지 함께 처리되는지 검증
    @Test
    void 셀러가_공동구매_생성에_성공한다() {
        Member seller = sellerMember();
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(seller));
        Product product = ownedProduct();
        when(productService.getOwnedOrThrow(1L, 10L)).thenReturn(product);
        when(groupBuyRepository.save(any(GroupBuy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        groupBuyService.create(1L, createRequest());

        verify(groupBuyRepository).save(any(GroupBuy.class));
        verify(groupBuyPriceRepository).saveAll(any());
        verify(groupBuyCounterRepository).initialize(any(), any());
    }

    // BUYER 역할의 회원이 공동구매 생성을 시도하면 GROUP_BUY_FORBIDDEN 예외가 발생하는지 검증
    @Test
    void 셀러가_아니면_공동구매_생성에_실패한다() {
        Member buyer = mock(Member.class);
        when(buyer.getRole()).thenReturn(Role.BUYER);
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(buyer));

        assertThatThrownBy(() -> groupBuyService.create(1L, createRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_FORBIDDEN);
        verify(groupBuyRepository, never()).save(any());
    }

    // 요청한 productId가 본인이 등록한 상품이 아니면(미존재/타인 소유) PRODUCT_NOT_FOUND로 즉시 거부되는지 검증
    @Test
    void 등록되지_않은_상품이면_생성에_실패한다() {
        Member seller = sellerMember();
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(seller));
        when(productService.getOwnedOrThrow(1L, 10L)).thenThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        assertThatThrownBy(() -> groupBuyService.create(1L, createRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        verify(groupBuyRepository, never()).save(any());
    }

    // 시작일이 마감일보다 이후이면 GROUP_BUY_INVALID_PERIOD 예외가 발생하는지 검증
    @Test
    void 시작일이_마감일보다_늦으면_생성에_실패한다() {
        Member seller = sellerMember();
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(seller));
        Product product = ownedProduct();
        when(productService.getOwnedOrThrow(1L, 10L)).thenReturn(product);
        GroupBuyCreateRequest invalidRequest = new GroupBuyCreateRequest(10L, "제목",
                LocalDateTime.now().plusDays(8), LocalDateTime.now().plusDays(1),
                100, 10_000, List.of(new PriceTierRequest(1, 100, 15_000)));

        assertThatThrownBy(() -> groupBuyService.create(1L, invalidRequest))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_INVALID_PERIOD);
        verify(groupBuyRepository, never()).save(any());
    }

    // 최소 수량이 최대 수량보다 크면 GROUP_BUY_INVALID_QUANTITY 예외가 발생하는지 검증
    @Test
    void 최소_수량이_최대_수량보다_크면_생성에_실패한다() {
        Member seller = sellerMember();
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(seller));
        Product product = ownedProduct();
        when(productService.getOwnedOrThrow(1L, 10L)).thenReturn(product);
        GroupBuyCreateRequest invalidRequest = new GroupBuyCreateRequest(10L, "제목",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8),
                10_000, 100, List.of(new PriceTierRequest(1, 100, 15_000)));

        assertThatThrownBy(() -> groupBuyService.create(1L, invalidRequest))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_INVALID_QUANTITY);
        verify(groupBuyRepository, never()).save(any());
    }

    // 생산자 본인이 시작 전(READY) 공동구매를 취소하면 상태가 CANCELED로 바뀌고 Redis 카운터가 정리되며 이벤트가 발행되는지 검증
    @Test
    void 생산자가_시작_전_공동구매_취소에_성공한다() {
        GroupBuy groupBuy = GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8), 100, 10_000);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));

        groupBuyService.cancel(1L, 1L);

        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.CANCELED);
        verify(groupBuyCounterRepository).delete(1L);
        verify(groupBuyEventPublisher, times(1)).publishCanceled(groupBuy);
    }

    // 생산자 본인이 아닌 회원이 취소를 시도하면 GROUP_BUY_FORBIDDEN 예외가 발생하는지 검증
    @Test
    void 생산자가_아니면_취소에_실패한다() {
        GroupBuy groupBuy = GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8), 100, 10_000);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));

        assertThatThrownBy(() -> groupBuyService.cancel(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_FORBIDDEN);
        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.READY);
    }

    // 이미 시작된(ONGOING) 공동구매는 취소할 수 없어 GROUP_BUY_CANCEL_NOT_ALLOWED 예외가 발생하는지 검증
    @Test
    void 이미_시작된_공동구매는_취소에_실패한다() {
        GroupBuy groupBuy = GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(8), 100, 10_000);
        groupBuy.start();
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));

        assertThatThrownBy(() -> groupBuyService.cancel(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_CANCEL_NOT_ALLOWED);
        verify(groupBuyCounterRepository, never()).delete(anyLong());
    }

    // 상세 조회 시마다 홈 화면 "인기" 정렬용 조회수를 원자적으로 1 증가시키는지 검증 (엔티티를 읽어 자바에서
    // +1 하는 대신 GroupBuyRepository.increaseViewCount로 DB에 직접 반영하는지가 핵심)
    @Test
    void 상세_조회_시_조회수를_증가시킨다() {
        GroupBuy groupBuy = GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8), 100, 10_000);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));
        when(groupBuyPriceRepository.findByGroupBuyIdOrderByTierOrderAsc(1L)).thenReturn(List.of());
        when(productRepository.findById(10L)).thenReturn(Optional.empty());

        groupBuyService.getDetail(1L);

        verify(groupBuyRepository, times(1)).increaseViewCount(1L);
    }

    private GroupBuy groupBuyWithId(Long id, Long productId, GroupBuyStatus status, int currentQuantity,
            int minQuantity) {
        GroupBuy groupBuy = GroupBuy.create(productId, 1L, "제목",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(8), minQuantity, 10_000);
        ReflectionTestUtils.setField(groupBuy, "id", id);
        ReflectionTestUtils.setField(groupBuy, "status", status);
        ReflectionTestUtils.setField(groupBuy, "currentQuantity", currentQuantity);
        return groupBuy;
    }

    // 검색 색인용 배치 조회 - 진행 중(ONGOING)인 공동구매는 참여 수량 기준 가격 구간에서 계산한 단가를 포함해 요약되는지 검증
    // (GroupBuyPriceCalculator를 그대로 재사용하는지가 핵심 - 새로 계산 로직을 만들지 않는다)
    @Test
    void 상품별_진행중인_공동구매_요약을_배치로_조회한다() {
        GroupBuy groupBuy = groupBuyWithId(1L, 10L, GroupBuyStatus.ONGOING, 150, 100);
        when(groupBuyRepository.findByProductIdInAndStatusIn(List.of(10L), List.of(GroupBuyStatus.READY, GroupBuyStatus.ONGOING)))
                .thenReturn(List.of(groupBuy));
        when(groupBuyPriceRepository.findByGroupBuyIdIn(List.of(1L))).thenReturn(List.of(
                GroupBuyPrice.of(1L, 1, 100, 15_000),
                GroupBuyPrice.of(1L, 2, 1_000, 12_000)));

        Map<Long, GroupBuyProductSummaryResponse> result = groupBuyService.getActiveSummariesByProductIds(List.of(10L));

        assertThat(result).containsOnlyKeys(10L);
        GroupBuyProductSummaryResponse summary = result.get(10L);
        assertThat(summary.groupBuyId()).isEqualTo(1L);
        assertThat(summary.groupBuyStatus()).isEqualTo(GroupBuyStatus.ONGOING);
        assertThat(summary.currentUnitPrice()).isEqualTo(15_000);
        assertThat(summary.currentQuantity()).isEqualTo(150);
        assertThat(summary.targetQuantity()).isEqualTo(100);
        assertThat(summary.maxQuantity()).isEqualTo(10_000);
        assertThat(summary.endAt()).isEqualTo(groupBuy.getEndAt());
    }

    // 진행 중인(READY/ONGOING) 공동구매가 없는 상품은 결과 Map에서 아예 빠지는지 검증
    @Test
    void 진행중인_공동구매가_없는_상품은_결과에서_빠진다() {
        when(groupBuyRepository.findByProductIdInAndStatusIn(List.of(20L), List.of(GroupBuyStatus.READY, GroupBuyStatus.ONGOING)))
                .thenReturn(List.of());

        Map<Long, GroupBuyProductSummaryResponse> result = groupBuyService.getActiveSummariesByProductIds(List.of(20L));

        assertThat(result).isEmpty();
    }

    // 한 상품에 READY/ONGOING 공동구매가 동시에 있으면(생성 시점에 막고 있지 않아 이론상 가능) ONGOING을 대표로 고르는지 검증
    @Test
    void 같은_상품에_진행중인_공동구매가_여러건이면_ONGOING을_대표로_고른다() {
        GroupBuy readyGroupBuy = groupBuyWithId(1L, 30L, GroupBuyStatus.READY, 0, 100);
        GroupBuy ongoingGroupBuy = groupBuyWithId(2L, 30L, GroupBuyStatus.ONGOING, 50, 100);
        when(groupBuyRepository.findByProductIdInAndStatusIn(List.of(30L), List.of(GroupBuyStatus.READY, GroupBuyStatus.ONGOING)))
                .thenReturn(List.of(readyGroupBuy, ongoingGroupBuy));
        when(groupBuyPriceRepository.findByGroupBuyIdIn(List.of(2L))).thenReturn(
                List.of(GroupBuyPrice.of(2L, 1, 0, 20_000)));

        Map<Long, GroupBuyProductSummaryResponse> result = groupBuyService.getActiveSummariesByProductIds(List.of(30L));

        assertThat(result.get(30L).groupBuyStatus()).isEqualTo(GroupBuyStatus.ONGOING);
        assertThat(result.get(30L).currentQuantity()).isEqualTo(50);
    }
}
