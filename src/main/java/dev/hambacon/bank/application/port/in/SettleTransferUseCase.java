package dev.hambacon.bank.application.port.in;

import java.util.UUID;

public interface SettleTransferUseCase {
    void completeTransfer(UUID transferId);

    void failTransfer(UUID transferId);
}
