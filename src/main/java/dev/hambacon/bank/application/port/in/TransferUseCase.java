package dev.hambacon.bank.application.port.in;

import dev.hambacon.bank.domain.Transfer;

import java.util.UUID;

public interface TransferUseCase {
    Transfer acceptTransfer(UUID sourceAccountId, UUID destinationAccountId, long amountMinor, String idempotencyKey);

    Transfer getTransfer(UUID transferId);
}
