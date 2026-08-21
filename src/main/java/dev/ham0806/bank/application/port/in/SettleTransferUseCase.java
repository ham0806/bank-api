package dev.ham0806.bank.application.port.in;

import java.util.UUID;

public interface SettleTransferUseCase {
    void completeTransfer(UUID transferId);

    void failTransfer(UUID transferId);
}
