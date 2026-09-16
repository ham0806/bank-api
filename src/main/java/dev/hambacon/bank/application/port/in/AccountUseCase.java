package dev.hambacon.bank.application.port.in;

import dev.hambacon.bank.domain.Account;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AccountUseCase {
    Account createAccount(String accountNumber);

    Account getAccount(UUID accountId);

    List<TransactionView> getTransactions(UUID accountId);

    OperationResult deposit(UUID accountId, long amountMinor, String idempotencyKey);

    OperationResult withdraw(UUID accountId, long amountMinor, String idempotencyKey);

    record OperationResult(UUID transactionId, UUID accountId, long amountMinor, long balanceMinor) {}

    record TransactionView(
            UUID transactionId,
            UUID referenceId,
            String kind,
            long amountMinor,
            OffsetDateTime createdAt
    ) {}
}
