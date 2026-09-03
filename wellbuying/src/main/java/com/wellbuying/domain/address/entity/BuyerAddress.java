package com.wellbuying.domain.address.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

// 회원이 재사용할 수 있는 배송지 주소록. 공동구매 참여 시 텍스트를 매번 새로 받는 대신 이 중 하나를
// buyer_address_id로 참조한다 - 참여 건에 주소 텍스트를 스냅샷으로 복사해두던 이전 방식과 달리, 이후 이
// 주소록 항목을 수정하면 과거 참여 건에 표시되는 주소도 함께 바뀐다(재사용 편의를 우선한 설계)
@Entity
@Table(name = "buyer_address")
public class BuyerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String address;

    @Column(name = "address_detail")
    private String addressDetail;

    @Column(nullable = false)
    private String zipcode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected BuyerAddress() {
    }

    private BuyerAddress(Long memberId, String address, String addressDetail, String zipcode) {
        this.memberId = memberId;
        this.address = address;
        this.addressDetail = addressDetail;
        this.zipcode = zipcode;
    }

    public static BuyerAddress create(Long memberId, String address, String addressDetail, String zipcode) {
        return new BuyerAddress(memberId, address, addressDetail, zipcode);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getAddress() {
        return address;
    }

    public String getAddressDetail() {
        return addressDetail;
    }

    public String getZipcode() {
        return zipcode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
