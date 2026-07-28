package dev.hambacon.bank.application;

import dev.hambacon.bank.domain.LedgerLine;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface LedgerRepository {
    UUID post(UUID referenceId, String kind, List<LedgerLine> lines);
    List<TransactionView> findByAccountId(UUID accountId);

    record TransactionView(UUID transactionId, UUID referenceId, String kind, long amountMinor,
                           OffsetDateTime createdAt) {}
}

