package dev.matheus.payment.domain.model;

import dev.matheus.payment.domain.exception.InsufficientBalanceException;
import dev.matheus.payment.domain.exception.InvalidMoneyException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class Money {

    private static final String CURRENCY = "BRL";
    private static final int SCALE = 2;

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount;
    }

    public static Money ofTransfer(BigDecimal amount) {
        BigDecimal normalized = normalize(amount);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidMoneyException("transfer amount must be greater than zero");
        }
        return new Money(normalized);
    }

    public static Money ofBalance(BigDecimal amount) {
        BigDecimal normalized = normalize(amount);
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidMoneyException("balance cannot be negative");
        }
        return new Money(normalized);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY));
    }

    private static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidMoneyException("amount is required");
        }
        try {
            return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new InvalidMoneyException("amount must have at most 2 decimal places");
        }
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "other money is required");
        return ofBalance(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        Objects.requireNonNull(other, "other money is required");
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException("insufficient balance");
        }
        return ofBalance(result);
    }

    public boolean isGreaterThan(Money other) {
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        return this.amount.compareTo(other.amount) >= 0;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return CURRENCY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money money)) {
            return false;
        }
        return amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return CURRENCY + " " + amount;
    }
}
