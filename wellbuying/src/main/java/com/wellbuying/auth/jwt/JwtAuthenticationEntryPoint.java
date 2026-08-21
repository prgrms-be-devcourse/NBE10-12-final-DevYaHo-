package com.wellbuying.auth.jwt;

import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 인증 실패 시 호출 - 필터에서 세팅한 구체적 에러코드(만료/위조 등)가 있으면 사용, 없으면 AUTHENTICATION_REQUIRED로 401 응답
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        ErrorCode errorCode = (ErrorCode) request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE);
        if (errorCode == null) {
            errorCode = ErrorCode.AUTHENTICATION_REQUIRED;
        }
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }
}
