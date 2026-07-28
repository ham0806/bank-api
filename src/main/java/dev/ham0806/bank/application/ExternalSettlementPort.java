package dev.ham0806.bank.application;

import dev.ham0806.bank.domain.Transfer;

public interface ExternalSettlementPort {
    Result settle(Transfer transfer);

    enum Result { SUCCESS, RETRYABLE_FAILURE, PERMANENT_FAILURE }
}

