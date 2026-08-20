package com.wellbuying;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WellbuyingApplication {

    // Spring Boot 애플리케이션 진입점
    public static void main(String[] args) {
        SpringApplication.run(WellbuyingApplication.class, args);
    }

}
