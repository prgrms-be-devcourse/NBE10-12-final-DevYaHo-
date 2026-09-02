package com.wellbuying.global.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /error로 forward되는 모든 응답(403/404/405 등)을 단일 지점에서 원래 상태코드로 되살려 응답 - phase16 트러블슈팅 참고
@RestController
public class GlobalErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = (statusAttr != null)
                ? HttpStatus.valueOf((Integer) statusAttr)
                : HttpStatus.INTERNAL_SERVER_ERROR;

        ErrorCode errorCode = switch (status) {
            case FORBIDDEN -> ErrorCode.COMMON_403_FORBIDDEN;
            case NOT_FOUND -> ErrorCode.COMMON_404_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCode.COMMON_405_METHOD_NOT_ALLOWED;
            default -> null;
        };

        ErrorResponse body = (errorCode != null)
                ? ErrorResponse.of(errorCode)
                : new ErrorResponse("COMMON_" + status.value() + "_ERROR", status.getReasonPhrase());
        return ResponseEntity.status(status).body(body);
    }
}
