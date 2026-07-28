package dev.hambacon.bank.application;

import dev.hambacon.bank.domain.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    Account create(String accountNumber);
    Optional<Account> findById(UUID accountId);
    Account findByIdForUpdate(UUID accountId);
    void update(Account account);
}

