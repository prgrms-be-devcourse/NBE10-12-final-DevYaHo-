package com.wellbuying.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;

// GlobalExceptionHandler는 Spring 컨텍스트 없이도 순수하게 동작하므로
// @WebMvcTest 없이 직접 인스턴스를 만들어 메서드를 호출하는 단위 테스트로 검증한다
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusinessException_4xx_에러코드는_그대로_상태코드와_메시지를_반환한다() {
        BusinessException e = new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(e);

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND.getStatus());
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND.getCode());
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND.getMessage());
    }

    @Test
    void handleBusinessException_5xx_에러코드도_동일하게_상태코드와_메시지를_반환한다() {
        BusinessException e = new BusinessException(ErrorCode.COMMON_500_INTERNAL_SERVER_ERROR);

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.COMMON_500_INTERNAL_SERVER_ERROR.getCode());
        // 5xx는 로그를 남기는 분기이므로(log.error 호출), 여기서는 응답 자체의 정확성만 검증한다.
        // 로그 호출 여부까지 검증하려면 Logback ListAppender를 추가하는 방법이 있으나
        // 이번 테스트에서는 응답 정확성 검증에 집중한다.
    }

    @Test
    void handleDataIntegrityViolationException_항상_409_DUPLICATE_RESOURCE로_변환한다() {
        DataIntegrityViolationException e = new DataIntegrityViolationException("unique constraint violated");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolationException(e);

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE.getStatus());
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE.getCode());
    }

    @Test
    void handleMethodArgumentNotValidException_필드_에러_메시지를_정렬해서_결합한다() {
        MethodArgumentNotValidException e = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "startPrice", "가격은 0 이상이어야 합니다"));
        bindingResult.addError(new FieldError("request", "productName", "상품명은 필수입니다"));
        when(e.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValidException(e);

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INVALID_INPUT.getStatus());
        // 정렬 순서 확인 - productName이 startPrice보다 사전순으로 앞섬
        assertThat(response.getBody().message())
                .isEqualTo("productName: 상품명은 필수입니다, startPrice: 가격은 0 이상이어야 합니다");
    }

    @Test
    void handleMethodArgumentNotValidException_에러_메시지가_없으면_기본_메시지를_사용한다() {
        MethodArgumentNotValidException e = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        when(e.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValidException(e);

        assertThat(response.getBody().message()).isEqualTo(ErrorCode.INVALID_INPUT.getMessage());
    }

    @Test
    void handleMethodArgumentNotValidException_ObjectError가_섞여도_정상_처리한다() {
        MethodArgumentNotValidException e = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "amount", "수량은 1 이상이어야 합니다"));
        bindingResult.addError(new ObjectError("request", "전체 요청 조건이 유효하지 않습니다"));
        when(e.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValidException(e);

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INVALID_INPUT.getStatus());
        // ObjectError는 formatErrorMessage()에서 error.getObjectName()을 필드명으로 사용함
        assertThat(response.getBody().message())
                .isEqualTo("amount: 수량은 1 이상이어야 합니다, request: 전체 요청 조건이 유효하지 않습니다");
    }

    @Test
    void handleConstraintViolationException_위반_필드명과_메시지를_결합한다() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        Path.Node node = mock(Path.Node.class);
        when(node.getName()).thenReturn("page");
        when(path.iterator()).thenReturn(List.of(node).iterator());
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be >= 0");
        ConstraintViolationException e = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolationException(e);

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INVALID_INPUT.getStatus());
        assertThat(response.getBody().message()).isEqualTo("page: must be >= 0");
    }

    @Test
    void handleConstraintViolationException_위반_목록이_비어있으면_기본_메시지를_사용한다() {
        ConstraintViolationException e = new ConstraintViolationException(Set.of());

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolationException(e);

        assertThat(response.getBody().message()).isEqualTo(ErrorCode.INVALID_INPUT.getMessage());
    }
}
