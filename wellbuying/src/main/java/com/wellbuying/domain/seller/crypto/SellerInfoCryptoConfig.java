package com.wellbuying.domain.seller.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// @DataJpaTest 등 슬라이스 테스트는 컴포넌트 스캔을 JPA 관련 타입으로 제한해 @ConfigurationPropertiesScan을 타지 않는다.
// 이 클래스 자체도 슬라이스의 컴포넌트 스캔 대상은 아니며, src/test/resources/META-INF/spring/
// org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest.imports 에 명시적으로 등록해서
// (QuerydslConfig와 동일한 방식) 슬라이스 테스트에서도 이 설정이 로드되도록 한다.
@Configuration
@EnableConfigurationProperties(SellerInfoEncryptionProperties.class)
public class SellerInfoCryptoConfig {
}
