package dev.hambacon.bank.application;

public class IdempotencyConflictException extends BankingException {
    public IdempotencyConflictException(String message) { super(message); }
}

