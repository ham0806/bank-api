package dev.ham0806.bank.application.port.in;

import dev.ham0806.bank.domain.Transfer;

import java.util.UUID;

public interface TransferUseCase {
    Transfer acceptTransfer(UUID sourceAccountId, UUID destinationAccountId, long amountMinor, String idempotencyKey);

    Transfer getTransfer(UUID transferId);
}
