package dev.hambacon.bank.domain;

public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(String message) { super(message); }
}

