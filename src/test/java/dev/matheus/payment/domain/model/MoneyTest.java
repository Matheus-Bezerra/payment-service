package dev.matheus.payment.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.matheus.payment.domain.exception.InsufficientBalanceException;
import dev.matheus.payment.domain.exception.InvalidMoneyException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void transferAmountMustBePositive() {
        assertThrows(InvalidMoneyException.class, () -> Money.ofTransfer(BigDecimal.ZERO));
        assertThrows(InvalidMoneyException.class, () -> Money.ofTransfer(new BigDecimal("-1.00")));
    }

    @Test
    void balanceCannotBeNegative() {
        assertThrows(InvalidMoneyException.class, () -> Money.ofBalance(new BigDecimal("-0.01")));
    }

    @Test
    void subtractFailsWhenInsufficient() {
        Money balance = Money.ofBalance(new BigDecimal("10.00"));
        Money amount = Money.ofTransfer(new BigDecimal("10.01"));
        assertThrows(InsufficientBalanceException.class, () -> balance.subtract(amount));
    }

    @Test
    void addAndSubtractWork() {
        Money balance = Money.ofBalance(new BigDecimal("100.00"));
        Money result = balance.subtract(Money.ofTransfer(new BigDecimal("40.00")))
                .add(Money.ofTransfer(new BigDecimal("5.00")));
        assertEquals(new BigDecimal("65.00"), result.amount());
        assertTrue(Money.ofTransfer(new BigDecimal("65.00")).equals(result));
    }
}
