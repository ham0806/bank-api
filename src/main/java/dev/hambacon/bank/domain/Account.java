package dev.hambacon.bank.domain;

import java.util.UUID;

public record Account(
        UUID id,
        String accountNumber,
        long balanceMinor,
        long reservedMinor,
        long version,
        AccountStatus status
) {
    public long availableMinor() {
        return balanceMinor - reservedMinor;
    }

    public Account reserve(Money amount) {
        var amountMinor = amount.minor();
        if (availableMinor() < amountMinor) {
            throw new InsufficientFundsException("利用可能残高が不足しています");
        }
        return new Account(id, accountNumber, balanceMinor, Math.addExact(reservedMinor, amountMinor), version + 1, status);
    }

    public Account releaseReservation(Money amount) {
        var amountMinor = amount.minor();
        if (reservedMinor < amountMinor) {
            throw new IllegalStateException("予約残高が不整合です");
        }
        return new Account(id, accountNumber, balanceMinor, reservedMinor - amountMinor, version + 1, status);
    }

    public Account deposit(Money amount) {
        var amountMinor = amount.minor();
        return new Account(id, accountNumber, Math.addExact(balanceMinor, amountMinor), reservedMinor, version + 1, status);
    }

    public Account withdraw(Money amount) {
        var amountMinor = amount.minor();
        if (availableMinor() < amountMinor) {
            throw new InsufficientFundsException("利用可能残高が不足しています");
        }
        return new Account(id, accountNumber, balanceMinor - amountMinor, reservedMinor, version + 1, status);
    }

    public Account settleReservedDebit(Money amount) {
        var amountMinor = amount.minor();
        if (reservedMinor < amountMinor || balanceMinor < amountMinor) {
            throw new IllegalStateException("振込予約を確定できません");
        }
        return new Account(id, accountNumber, balanceMinor - amountMinor, reservedMinor - amountMinor, version + 1, status);
    }

    public Account applyLedgerDelta(long deltaMinor) {
        if (deltaMinor == 0) {
            throw new InvalidAmountException("仕訳差額は0以外でなければなりません");
        }
        var updatedBalance = Math.addExact(balanceMinor, deltaMinor);
        if (status != AccountStatus.SYSTEM && updatedBalance < 0) {
            throw new InsufficientFundsException("口座残高が不足しています");
        }
        return new Account(id, accountNumber, updatedBalance, reservedMinor, version + 1, status);
    }

}
