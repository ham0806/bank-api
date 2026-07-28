package dev.hambacon.bank.domain;

public class InvalidTransferStateException extends RuntimeException {
    public InvalidTransferStateException(String message) { super(message); }
}

