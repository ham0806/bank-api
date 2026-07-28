package dev.hambacon.bank.application;

public class ResourceNotFoundException extends BankingException {
    public ResourceNotFoundException(String message) { super(message); }
}

