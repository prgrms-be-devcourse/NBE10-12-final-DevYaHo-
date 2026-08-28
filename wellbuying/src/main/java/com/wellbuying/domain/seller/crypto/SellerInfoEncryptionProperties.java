package com.wellbuying.domain.seller.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

// SellerInfo 금융정보(계좌번호/예금주명) 암호화 키 - Base64로 인코딩된 AES-256(32바이트) 키
@ConfigurationProperties(prefix = "seller-info.encryption")
public record SellerInfoEncryptionProperties(String key) {
}
