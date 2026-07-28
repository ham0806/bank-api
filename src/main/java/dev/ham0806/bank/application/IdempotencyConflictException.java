package dev.ham0806.bank.application;

public class IdempotencyConflictException extends BankingException {
    public IdempotencyConflictException(String message) { super(message); }
}

