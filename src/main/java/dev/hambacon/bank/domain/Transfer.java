package dev.hambacon.bank.domain;

import java.util.UUID;

public record Transfer(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        long amountMinor,
        TransferStatus status
) {
    public Transfer complete() {
        if (status != TransferStatus.PENDING) {
            throw new InvalidTransferStateException("PENDING以外の振込は完了できません");
        }
        return new Transfer(id, sourceAccountId, destinationAccountId, amountMinor, TransferStatus.COMPLETED);
    }

    public Transfer fail() {
        if (status != TransferStatus.PENDING) {
            throw new InvalidTransferStateException("PENDING以外の振込は失敗にできません");
        }
        return new Transfer(id, sourceAccountId, destinationAccountId, amountMinor, TransferStatus.FAILED);
    }
}

