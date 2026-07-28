package dev.hambacon.bank.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {
    @Test
    void 金額が0以下の場合は拒否する() {
        assertThatThrownBy(() -> Money.of(0))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> Money.of(-1))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void 加算のオーバーフローを拒否する() {
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE).add(Money.of(1)))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void 減算結果が正でなければ拒否する() {
        assertThatThrownBy(() -> Money.of(100).subtract(Money.of(101)))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> Money.of(100).subtract(Money.of(100)))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void 金額を加減算できる() {
        var result = Money.of(1_000).add(Money.of(500)).subtract(Money.of(200));

        assertThat(result.minor()).isEqualTo(1_300);
    }
}
