package dev.hambacon.bank.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {
    private final Account account = new Account(UUID.randomUUID(), "A-1", 1_000, 0, 0, AccountStatus.ACTIVE);

    @Test
    void 入金すると残高とバージョンが増える() {
        var updated = account.deposit(Money.of(500));

        assertThat(updated.balanceMinor()).isEqualTo(1_500);
        assertThat(updated.version()).isEqualTo(1);
    }

    @Test
    void 利用可能残高を超える出金は拒否する() {
        assertThatThrownBy(() -> account.withdraw(Money.of(1_001)))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void 金額0は拒否する() {
        assertThatThrownBy(() -> account.deposit(Money.of(0)))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void 予約額を確定すると残高と予約額が減る() {
        var reserved = account.reserve(Money.of(400));
        var settled = reserved.settleReservedDebit(Money.of(400));

        assertThat(settled.balanceMinor()).isEqualTo(600);
        assertThat(settled.reservedMinor()).isZero();
    }
}
