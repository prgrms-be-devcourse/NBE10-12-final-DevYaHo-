package com.wellbuying.domain.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.address.dto.BuyerAddressCreateRequest;
import com.wellbuying.domain.address.dto.BuyerAddressResponse;
import com.wellbuying.domain.address.entity.BuyerAddress;
import com.wellbuying.domain.address.repository.BuyerAddressRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BuyerAddressServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long ADDRESS_ID = 100L;

    @Mock
    private BuyerAddressRepository buyerAddressRepository;

    @InjectMocks
    private BuyerAddressService buyerAddressService;

    private BuyerAddress addressOwnedBy(Long memberId, boolean isDefault) {
        BuyerAddress address = BuyerAddress.create(memberId, "서울시 강남구 123", "4층", "06000", isDefault);
        ReflectionTestUtils.setField(address, "id", ADDRESS_ID);
        return address;
    }

    @Test
    @DisplayName("create: 요청에서 명시적으로 기본 배송지를 지정하면 기존 기본 배송지를 해제하고 새 배송지를 기본으로 저장한다")
    void create_요청에서_명시적으로_기본_지정() {
        // request.isDefault()==true이면 단락 평가로 existsByMemberId는 호출되지 않는다
        BuyerAddress existingDefault = addressOwnedBy(MEMBER_ID, true);
        when(buyerAddressRepository.findByMemberIdAndIsDefaultTrue(MEMBER_ID)).thenReturn(Optional.of(existingDefault));
        when(buyerAddressRepository.save(any(BuyerAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BuyerAddressCreateRequest request = new BuyerAddressCreateRequest("서울시 서초구 456", null, "07000", true);

        BuyerAddressResponse response = buyerAddressService.create(MEMBER_ID, request);

        assertThat(existingDefault.isDefault()).isFalse();
        assertThat(response.isDefault()).isTrue();
        ArgumentCaptor<BuyerAddress> captor = ArgumentCaptor.forClass(BuyerAddress.class);
        verify(buyerAddressRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isTrue();
    }

    @Test
    @DisplayName("create: 회원의 첫 배송지면 요청에서 지정하지 않아도 자동으로 기본 배송지가 된다")
    void create_첫_배송지는_자동으로_기본이_된다() {
        when(buyerAddressRepository.existsByMemberId(MEMBER_ID)).thenReturn(false);
        when(buyerAddressRepository.findByMemberIdAndIsDefaultTrue(MEMBER_ID)).thenReturn(Optional.empty());
        when(buyerAddressRepository.save(any(BuyerAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BuyerAddressCreateRequest request = new BuyerAddressCreateRequest("서울시 서초구 456", null, "07000", false);

        BuyerAddressResponse response = buyerAddressService.create(MEMBER_ID, request);

        assertThat(response.isDefault()).isTrue();
    }

    @Test
    @DisplayName("create: 이미 다른 배송지가 있고 요청도 기본 지정이 아니면 기본 배송지 조회 없이 기본이 아닌 채로 저장한다")
    void create_기존_기본_배송지가_있고_요청도_기본이_아니면_그대로_저장() {
        when(buyerAddressRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);
        when(buyerAddressRepository.save(any(BuyerAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BuyerAddressCreateRequest request = new BuyerAddressCreateRequest("서울시 서초구 456", null, "07000", false);

        BuyerAddressResponse response = buyerAddressService.create(MEMBER_ID, request);

        assertThat(response.isDefault()).isFalse();
        verify(buyerAddressRepository, never()).findByMemberIdAndIsDefaultTrue(any());
    }

    @Test
    @DisplayName("delete: 본인 소유가 아닌 주소로 호출하면 BUYER_ADDRESS_FORBIDDEN 예외를 던지고 삭제하지 않는다")
    void delete_본인_소유가_아니면_예외() {
        BuyerAddress address = addressOwnedBy(OTHER_MEMBER_ID, false);
        when(buyerAddressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address));

        assertThatThrownBy(() -> buyerAddressService.delete(MEMBER_ID, ADDRESS_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BUYER_ADDRESS_FORBIDDEN));
        verify(buyerAddressRepository, never()).delete(any());
    }

    @Test
    @DisplayName("setDefault: 본인 소유가 아닌 주소로 호출하면 BUYER_ADDRESS_FORBIDDEN 예외를 던진다")
    void setDefault_본인_소유가_아니면_예외() {
        BuyerAddress address = addressOwnedBy(OTHER_MEMBER_ID, false);
        when(buyerAddressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address));

        assertThatThrownBy(() -> buyerAddressService.setDefault(MEMBER_ID, ADDRESS_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BUYER_ADDRESS_FORBIDDEN));
    }

    @Test
    @DisplayName("setDefault: 이미 기본 배송지인 주소를 다시 지정하면 아무것도 하지 않고 조용히 반환한다")
    void setDefault_이미_기본이면_아무일도_하지_않는다() {
        BuyerAddress address = addressOwnedBy(MEMBER_ID, true);
        when(buyerAddressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address));

        buyerAddressService.setDefault(MEMBER_ID, ADDRESS_ID);

        verify(buyerAddressRepository, never()).findByMemberIdAndIsDefaultTrue(any());
        assertThat(address.isDefault()).isTrue();
    }

    @Test
    @DisplayName("setDefault: 이미 다른 기본 배송지가 있으면 그 배송지를 해제하고 대상 주소를 기본으로 전환한다")
    void setDefault_기존_기본_배송지를_해제하고_대상을_기본으로_지정() {
        BuyerAddress target = addressOwnedBy(MEMBER_ID, false);
        BuyerAddress previousDefault = BuyerAddress.create(MEMBER_ID, "서울시 마포구 789", null, "08000", true);
        ReflectionTestUtils.setField(previousDefault, "id", 200L);
        when(buyerAddressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(target));
        when(buyerAddressRepository.findByMemberIdAndIsDefaultTrue(MEMBER_ID)).thenReturn(Optional.of(previousDefault));

        buyerAddressService.setDefault(MEMBER_ID, ADDRESS_ID);

        assertThat(previousDefault.isDefault()).isFalse();
        assertThat(target.isDefault()).isTrue();
        verify(buyerAddressRepository, times(1)).findByMemberIdAndIsDefaultTrue(MEMBER_ID);
    }
}
