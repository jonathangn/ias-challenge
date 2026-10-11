package com.jonathangn.loancore.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import com.jonathangn.loancore.dto.ApiError;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            ResponseStatusException exception, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ApiError(
                        LocalDateTime.now(),
                        exception.getStatusCode().value(),
                        status != null ? status.getReasonPhrase() : "Error",
                        exception.getReason(),
                        exchange.getRequest().getPath().value()));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(
            ServerWebInputException exception, ServerWebExchange exchange) {
        return ResponseEntity.badRequest()
                .body(new ApiError(
                        LocalDateTime.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "Invalid request body",
                        exchange.getRequest().getPath().value()));
    }
}
