package com.wellbuying.global.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 도메인 비즈니스 예외(BusinessException)를 ErrorCode에 정의된 상태코드/메시지로 변환해 응답
    // 4xx는 정상 비즈니스 흐름(중복 이메일 등)이라 로그를 남기지 않고, 5xx만 확인이 필요한 결함이므로 ERROR로 로그
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        if (e.getErrorCode().getStatus().is5xxServerError()) {
            log.error("처리되지 않은 서버 오류: errorCode={}", e.getErrorCode().getCode(), e);
        }
        return ResponseEntity.status(e.getErrorCode().getStatus()).body(ErrorResponse.of(e.getErrorCode()));
    }

    // UNIQUE 제약 위반 등 사전 존재 체크를 통과했지만 DB 레벨에서 걸러진 경우(동시 요청 등) 500 대신 409로 응답
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        return ResponseEntity.status(ErrorCode.DUPLICATE_RESOURCE.getStatus())
                .body(ErrorResponse.of(ErrorCode.DUPLICATE_RESOURCE));
    }

    // @Valid 검증 실패 시 필드/클래스 레벨 에러를 모두 수집해 정렬 후 400 응답
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(this::formatErrorMessage)
                .sorted()
                .collect(Collectors.joining(", "));
        if (message.isBlank()) {
            message = ErrorCode.INVALID_INPUT.getMessage();
        }
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, message));
    }

    private String formatErrorMessage(ObjectError error) {
        String name = (error instanceof FieldError fieldError)
                ? fieldError.getField()
                : error.getObjectName();

        String rawMessage = error.getDefaultMessage();
        String message = StringUtils.hasText(rawMessage)
                ? rawMessage
                : ErrorCode.INVALID_INPUT.getMessage();

        return name + ": " + message;
    }

    // @Validated + @RequestParam 제약 위반(NotBlank/Min/Max 등)을 400으로 응답
    // 위반 항목을 모두 결합하고 propertyPath에서 파라미터명을 추출해 "page: must be >= 0" 형태로 응답
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();

        if (violations == null || violations.isEmpty()) {
            return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                    .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage()));
        }

        String message = violations.stream()
                .map(cv -> extractPropertyName(cv) + ": " + cv.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));

        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT,
                        message.isBlank() ? ErrorCode.INVALID_INPUT.getMessage() : message));
    }

    private String extractPropertyName(ConstraintViolation<?> violation) {
        Path path = violation.getPropertyPath();
        if (path == null) {
            return "unknown";
        }

        String propertyName = null;
        for (Path.Node node : path) {
            propertyName = node.getName();
        }

        return propertyName != null ? propertyName : path.toString();
    }

    // 잘못된 sort 필드명(?sort=wrongProperty)은 SQL이 생성되기 전 리포지토리 프록시 단계에서 실패 - 클라이언트 잘못이므로 500이 아닌 400으로 응답
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handlePropertyReferenceException(PropertyReferenceException e) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, "정렬할 수 없는 필드입니다: " + e.getPropertyName()));
    }
}
