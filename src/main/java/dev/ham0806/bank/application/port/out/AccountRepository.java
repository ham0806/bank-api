package dev.ham0806.bank.application.port.out;

import dev.ham0806.bank.domain.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    Account create(String accountNumber);

    Optional<Account> findById(UUID accountId);

    Account findByIdForUpdate(UUID accountId);

    void update(Account account);
}
