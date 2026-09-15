package dev.hambacon.bank.domain;

/**
 * 正の円単位金額を表す値オブジェクト。
 * 残高の0や清算口座の負残高はAccountが保持する別の概念であり、Moneyには含めない。
 */
public record Money(long minor) {
    public Money {
        if (minor <= 0) {
            throw new InvalidAmountException("金額は1以上でなければなりません");
        }
    }

    public static Money of(long minor) {
        return new Money(minor);
    }

    public Money add(Money other) {
        try {
            return new Money(Math.addExact(minor, other.minor));
        } catch (ArithmeticException exception) {
            throw new InvalidAmountException("金額が上限を超えています");
        }
    }

    public Money subtract(Money other) {
        try {
            return new Money(Math.subtractExact(minor, other.minor));
        } catch (ArithmeticException exception) {
            throw new InvalidAmountException("金額の減算結果が不正です");
        }
    }
}
