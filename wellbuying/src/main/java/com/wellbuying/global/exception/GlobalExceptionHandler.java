package com.wellbuying.global.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 도메인 비즈니스 예외(BusinessException)를 ErrorCode에 정의된 상태코드/메시지로 변환해 응답
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus()).body(ErrorResponse.of(e.getErrorCode()));
    }

    // UNIQUE 제약 위반 등 사전 존재 체크를 통과했지만 DB 레벨에서 걸러진 경우(동시 요청 등) 500 대신 409로 응답
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        return ResponseEntity.status(ErrorCode.DUPLICATE_RESOURCE.getStatus())
                .body(ErrorResponse.of(ErrorCode.DUPLICATE_RESOURCE));
    }

    // @Valid 검증 실패 시 첫 번째 필드 에러 메시지를 담아 400 응답
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse(ErrorCode.INVALID_INPUT.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, message));
    }
}
