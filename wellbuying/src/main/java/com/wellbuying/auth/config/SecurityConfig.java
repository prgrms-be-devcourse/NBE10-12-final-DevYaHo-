package com.wellbuying.auth.config;

import com.wellbuying.auth.jwt.JwtAuthenticationEntryPoint;
import com.wellbuying.auth.jwt.JwtAuthenticationFilter;
import com.wellbuying.auth.jwt.TokenProvider;
import com.wellbuying.auth.oauth.CustomOAuth2UserService;
import com.wellbuying.auth.oauth.LinkAwareOAuth2AuthorizationRequestResolver;
import com.wellbuying.auth.oauth.OAuth2AuthenticationFailureHandler;
import com.wellbuying.auth.oauth.OAuth2AuthenticationSuccessHandler;
import com.wellbuying.auth.oauth.SocialLinkTicketRepository;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PERMIT_ALL_PATHS = {
            "/api/auth/email/verification-code",
            "/api/auth/email/verify",
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/reissue",
            "/api/auth/seller/signup",
            "/api/auth/oauth/exchange",
            "/api/auth/reactivation/send",
            "/api/auth/reactivation/verify",
            "/api/auth/password-reissue/send",
            "/api/auth/password-reissue/verify",
            "/api/auth/password-reissue/reset",
            "/oauth2/**",
            "/login/oauth2/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            // 원래 요청의 인가 판단(403 등)이 끝난 뒤의 /error forward이므로 permitAll이어도 안전 - GlobalErrorController가 원래 상태코드를 그대로 응답
            "/error",
            // Prometheus(OCI)가 JWT 없이 스크레이핑 - 접근 제한은 Tailscale 사설망(네트워크 레벨)이 담당, 인터넷에는 미공개
            "/actuator/health",
            "/actuator/prometheus"
    };

    // 공동구매 조회(GET)는 로그인 없이도 둘러볼 수 있어야 하므로 별도로 permitAll 처리 - 생성/수정/참여 등 쓰기 작업은 인증 필요
    private static final String[] GROUP_BUY_PUBLIC_GET_PATHS = {
            "/api/groupBuys",
            "/api/groupBuys/*",
            "/api/groupBuys/*/status",
            "/api/groupBuys/*/price"
    };

    // 상품/카테고리 조회(GET)는 로그인 없이도 둘러볼 수 있어야 하므로 별도로 permitAll 처리
    private static final String[] PRODUCT_PUBLIC_GET_PATHS = {
            "/api/products",
            "/api/products/*",
            "/api/categories"
    };

    private final TokenProvider tokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CorsProperties corsProperties;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final SocialLinkTicketRepository socialLinkTicketRepository;

    public SecurityConfig(
            TokenProvider tokenProvider,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            CorsProperties corsProperties,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
            OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler,
            ClientRegistrationRepository clientRegistrationRepository,
            SocialLinkTicketRepository socialLinkTicketRepository
    ) {
        this.tokenProvider = tokenProvider;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.corsProperties = corsProperties;
        this.customOAuth2UserService = customOAuth2UserService;
        this.oAuth2AuthenticationSuccessHandler = oAuth2AuthenticationSuccessHandler;
        this.oAuth2AuthenticationFailureHandler = oAuth2AuthenticationFailureHandler;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.socialLinkTicketRepository = socialLinkTicketRepository;
    }

    // link_token 인지 authorizationRequestResolver를 Bean으로 등록 - securityFilterChain()이 파라미터로 주입받아 사용
    @Bean
    public LinkAwareOAuth2AuthorizationRequestResolver linkAwareOAuth2AuthorizationRequestResolver() {
        return new LinkAwareOAuth2AuthorizationRequestResolver(clientRegistrationRepository, socialLinkTicketRepository);
    }

    // /api/** 요청에 대한 CORS 허용 오리진/메서드/헤더 설정 (cors.allowed-origins로 배포 환경별 프론트 도메인 주입)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Device-Id"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    // 인증/인가 필터 체인 설정 - CORS 적용, CSRF/세션 비활성화(STATELESS), 회원가입/로그인만 permitAll, 나머지는 JWT 인증 필요
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            LinkAwareOAuth2AuthorizationRequestResolver linkAwareOAuth2AuthorizationRequestResolver) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_ALL_PATHS).permitAll()
                        // 컨트롤러의 @PreAuthorize가 누락되는 실수를 대비한 이중 방어선 - 관리자 API는 게이트웨이 레벨에서도 걸러낸다
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/groupBuys/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, GROUP_BUY_PUBLIC_GET_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, PRODUCT_PUBLIC_GET_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(
                                linkAwareOAuth2AuthorizationRequestResolver))
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler))
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
