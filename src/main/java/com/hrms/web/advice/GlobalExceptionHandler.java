package com.hrms.web.advice;

import com.hrms.domain.common.exception.BusinessException;
import com.hrms.infrastructure.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        // Try to get user info from security context
        String username = "UNKNOWN";
        Long userId = null;

        try {
            var currentUser = SecurityUtils.getCurrentUser();
            username = currentUser.username();
            userId = currentUser.userId();
        } catch (IllegalStateException e) {
            // User is not authenticated
        }

        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        } else {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        log.warn("ACCESS DENIED: user={} (ID:{}) from IP={} attempted {} {}",
                username, userId, ipAddress, request.getMethod(), request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, "VALIDATION_ERROR"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause().getMessage();
        if (msg != null && msg.contains("uq_users_email")) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Email này đã được sử dụng bởi tài khoản khác", "EMAIL_ALREADY_EXISTS"));
        }
        if (msg != null && msg.contains("username")) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Username đã tồn tại trong hệ thống", "USERNAME_ALREADY_EXISTS"));
        }
        log.warn("Data integrity violation: {}", msg);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Dữ liệu bị trùng lặp hoặc vi phạm ràng buộc", "DATA_INTEGRITY_ERROR"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        // Walk cause chain to detect DB constraint violations
        Throwable cause = ex;
        while (cause != null) {
            String msg = cause.getMessage();
            if (cause instanceof java.sql.SQLIntegrityConstraintViolationException
                    || (msg != null && msg.contains("Duplicate entry"))) {
                if (msg != null && msg.contains("uq_users_email")) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(ApiResponse.error("Email này đã được sử dụng bởi tài khoản khác", "EMAIL_ALREADY_EXISTS"));
                }
                if (msg != null && msg.contains("username")) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(ApiResponse.error("Username đã tồn tại trong hệ thống", "USERNAME_ALREADY_EXISTS"));
                }
                log.warn("Constraint violation: {}", msg);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error("Dữ liệu bị trùng lặp", "DATA_INTEGRITY_ERROR"));
            }
            cause = cause.getCause();
        }
        log.error("Unexpected error", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR"));
    }
}
