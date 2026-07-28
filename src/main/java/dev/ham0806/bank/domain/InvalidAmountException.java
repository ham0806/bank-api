package dev.ham0806.bank.domain;

public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(String message) { super(message); }
}

