package com.wellbuying;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
// Page<T>를 그대로 직렬화하면 JSON 구조가 안정적이지 않다는 경고가 있어 PagedModel(content + page 메타데이터)로 고정한다
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WellbuyingApplication {

    // Spring Boot 애플리케이션 진입점
    public static void main(String[] args) {
        SpringApplication.run(WellbuyingApplication.class, args);
    }

}
