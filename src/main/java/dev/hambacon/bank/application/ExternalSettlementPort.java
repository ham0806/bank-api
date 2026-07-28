package dev.hambacon.bank.application;

import dev.hambacon.bank.domain.Transfer;

public interface ExternalSettlementPort {
    Result settle(Transfer transfer);

    enum Result { SUCCESS, RETRYABLE_FAILURE, PERMANENT_FAILURE }
}

