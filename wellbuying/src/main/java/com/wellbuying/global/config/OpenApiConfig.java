package com.wellbuying.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    // 전역 보안 요건은 걸지 않는다 - permitAll 엔드포인트까지 자물쇠 아이콘이 붙어 오해를 줄 수 있어
    // 인증이 필요한 컨트롤러/메서드에만 @SecurityRequirement(name = BEARER_AUTH)를 개별 부여한다
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Wellbuying API")
                        .description("공동구매 플랫폼 wellbuying API 문서")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
