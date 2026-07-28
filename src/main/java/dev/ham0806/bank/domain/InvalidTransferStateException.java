package dev.ham0806.bank.domain;

public class InvalidTransferStateException extends RuntimeException {
    public InvalidTransferStateException(String message) { super(message); }
}

