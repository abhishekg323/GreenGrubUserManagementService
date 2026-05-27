package com.greengrub.usermanagement.exception;

import com.greengrub.usermanagement.dto.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void handleUserNotFoundException_returns404() {
        UserNotFoundException ex = new UserNotFoundException("user-001");
        ResponseEntity<ErrorResponse> response = handler.handleUserNotFoundException(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getError()).isEqualTo("User Not Found");
    }

    @Test
    void handleUserAlreadyExistsException_returns409() {
        UserAlreadyExistsException ex = new UserAlreadyExistsException("test@example.com");
        ResponseEntity<ErrorResponse> response = handler.handleUserAlreadyExistsException(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getStatus()).isEqualTo(409);
    }

    @Test
    void handleInvalidPasswordException_returns400() {
        InvalidPasswordException ex = new InvalidPasswordException("Invalid password");
        ResponseEntity<ErrorResponse> response = handler.handleInvalidPasswordException(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid password");
    }

    @Test
    void handleValidationException_returns400WithFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "email", "must not be blank");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getErrorCount()).thenReturn(1);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getValidationErrors()).containsKey("email");
    }

    @Test
    void handleTypeMismatchException_returns400() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getValue()).thenReturn("bad-value");
        when(ex.getName()).thenReturn("role");
        when(ex.getRequiredType()).thenReturn(null);
        when(ex.getMessage()).thenReturn("type mismatch");

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatchException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("Invalid Parameter");
    }

    @Test
    void handleIllegalArgumentException_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("bad input");
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("bad input");
    }

    @Test
    void handleUserStorageException_returns503() {
        UserStorageException ex = new UserStorageException("db down", new RuntimeException());
        ResponseEntity<ErrorResponse> response = handler.handleUserStorageException(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getStatus()).isEqualTo(503);
    }

    @Test
    void handleImageServiceException_returns503() {
        ImageServiceException ex = new ImageServiceException("image service down", null);
        ResponseEntity<ErrorResponse> response = handler.handleImageServiceException(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getError()).isEqualTo("Image Service Unavailable");
    }

    @Test
    void handleDonationServiceException_returns503() {
        DonationServiceException ex = new DonationServiceException("donation service down", null);
        ResponseEntity<ErrorResponse> response = handler.handleDonationServiceException(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getError()).isEqualTo("Donation Service Unavailable");
    }

    @Test
    void handleCallNotPermittedException_returns503() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("test");
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(cb);
        ResponseEntity<ErrorResponse> response = handler.handleCallNotPermittedException(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessage()).contains("overloaded");
    }

    @Test
    void handleGlobalException_returns500() {
        Exception ex = new RuntimeException("unexpected");
        ResponseEntity<ErrorResponse> response = handler.handleGlobalException(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getStatus()).isEqualTo(500);
    }

    @Test
    void errorResponse_includesPath() {
        UserNotFoundException ex = new UserNotFoundException("user-001");
        ResponseEntity<ErrorResponse> response = handler.handleUserNotFoundException(ex, request);
        assertThat(response.getBody().getPath()).isEqualTo("/api/test");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }
}
