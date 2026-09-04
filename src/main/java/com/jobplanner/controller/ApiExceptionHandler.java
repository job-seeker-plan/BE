package com.jobplanner.controller;

import com.jobplanner.service.EmailImportService;
import com.jobplanner.service.GmailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(GmailService.GmailTokenExpiredException.class)
    public ResponseEntity<Map<String, String>> handleGmailTokenExpired(GmailService.GmailTokenExpiredException error) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", error.getMessage()));
    }

    @ExceptionHandler(EmailImportService.EmailImportUnsupportedException.class)
    public ResponseEntity<Map<String, String>> handleEmailImportUnsupported(EmailImportService.EmailImportUnsupportedException error) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of("detail", error.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException error) {
        HttpStatus status = "Not authenticated".equals(error.getMessage()) ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Map.of("detail", error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException error) {
        return ResponseEntity.badRequest().body(Map.of("detail", "요청 값이 올바르지 않습니다."));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> handleServiceCall(RestClientException error) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("detail", "내부 AI 서비스를 일시적으로 사용할 수 없습니다."));
    }
}
