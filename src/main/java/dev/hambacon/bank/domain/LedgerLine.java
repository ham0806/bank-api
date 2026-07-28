package dev.hambacon.bank.domain;

import java.util.UUID;

public record LedgerLine(UUID accountId, long amountMinor) {
    public LedgerLine {
        if (amountMinor == 0) {
            throw new InvalidAmountException("仕訳金額は0以外でなければなりません");
        }
    }
}

