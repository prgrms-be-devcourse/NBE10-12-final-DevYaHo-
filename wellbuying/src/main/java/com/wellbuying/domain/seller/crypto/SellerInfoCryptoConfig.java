package com.wellbuying.domain.seller.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// @DataJpaTest 등 슬라이스 테스트는 @ConfigurationPropertiesScan을 타지 않지만, @Configuration 클래스는
// 슬라이스에서도 항상 컴포넌트 스캔에 포함되므로 여기서 명시적으로 등록해 슬라이스 테스트에서도 빈이 만들어지도록 한다.
@Configuration
@EnableConfigurationProperties(SellerInfoEncryptionProperties.class)
public class SellerInfoCryptoConfig {
}
