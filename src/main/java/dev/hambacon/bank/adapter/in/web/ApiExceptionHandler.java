package dev.hambacon.bank.adapter.in.web;

import dev.hambacon.bank.application.BankingException;
import dev.hambacon.bank.application.IdempotencyConflictException;
import dev.hambacon.bank.application.ResourceNotFoundException;
import dev.hambacon.bank.domain.InsufficientFundsException;
import dev.hambacon.bank.domain.InvalidAmountException;
import dev.hambacon.bank.domain.InvalidTransferStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    @ExceptionHandler({IdempotencyConflictException.class, InsufficientFundsException.class, InvalidTransferStateException.class})
    ResponseEntity<ProblemDetail> conflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Conflict", exception.getMessage());
    }

    @ExceptionHandler({BankingException.class, InvalidAmountException.class})
    ResponseEntity<ProblemDetail> badRequest(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "入力値が不正です");
        detail.setTitle("Validation failed");
        detail.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList()));
        return ResponseEntity.badRequest().body(detail);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detailText) {
        var detail = ProblemDetail.forStatusAndDetail(status, detailText);
        detail.setTitle(title);
        return ResponseEntity.status(status).body(detail);
    }

    record FieldErrorResponse(String field, String message) {}
}

