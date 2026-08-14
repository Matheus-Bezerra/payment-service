package dev.matheus.payment.domain.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.matheus.payment.domain.exception.DailyTransferLimitExceededException;
import dev.matheus.payment.domain.exception.TransferAmountLimitExceededException;
import dev.matheus.payment.domain.exception.TransferRateLimitExceededException;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.TransferPolicySnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TransferPolicyTest {

    @Test
    void pol01RejectsAboveDayCap() {
        TransferPolicySnapshot snapshot = snapshot(
                "20000.01",
                daytime(),
                "0.00",
                0
        );
        assertThrows(TransferAmountLimitExceededException.class, () -> TransferPolicy.assertAllowed(snapshot));
    }

    @Test
    void pol01AllowsExactDayCapDuringDay() {
        TransferPolicySnapshot snapshot = snapshot(
                "20000.00",
                daytime(),
                "0.00",
                0
        );
        assertDoesNotThrow(() -> TransferPolicy.assertAllowed(snapshot));
    }

    @Test
    void pol02RejectsAboveNightCap() {
        TransferPolicySnapshot snapshot = snapshot(
                "5000.01",
                nighttime(),
                "0.00",
                0
        );
        assertThrows(TransferAmountLimitExceededException.class, () -> TransferPolicy.assertAllowed(snapshot));
    }

    @Test
    void pol02AllowsExactNightCap() {
        TransferPolicySnapshot snapshot = snapshot(
                "5000.00",
                nighttime(),
                "0.00",
                0
        );
        assertDoesNotThrow(() -> TransferPolicy.assertAllowed(snapshot));
    }

    @Test
    void pol02AtSixUsesDayCap() {
        Instant sixAmSaoPaulo = LocalDateTime.of(2026, 8, 13, 6, 0)
                .atZone(TransferPolicySnapshot.ZONE)
                .toInstant();
        TransferPolicySnapshot snapshot = snapshot(
                "20000.00",
                sixAmSaoPaulo,
                "0.00",
                0
        );
        assertDoesNotThrow(() -> TransferPolicy.assertAllowed(snapshot));
    }

    @Test
    void pol03RejectsWhenDailyLimitExceeded() {
        TransferPolicySnapshot snapshot = snapshot(
                "1000.01",
                daytime(),
                "79000.00",
                0
        );
        assertThrows(DailyTransferLimitExceededException.class, () -> TransferPolicy.assertAllowed(snapshot));
    }

    @Test
    void pol03AllowsWhenDailyLimitExact() {
        TransferPolicySnapshot snapshot = snapshot(
                "1000.00",
                daytime(),
                "79000.00",
                0
        );
        assertDoesNotThrow(() -> TransferPolicy.assertAllowed(snapshot));
    }

    @Test
    void pol04RejectsWhenRateLimitReached() {
        TransferPolicySnapshot snapshot = snapshot(
                "10.00",
                daytime(),
                "0.00",
                5
        );
        assertThrows(TransferRateLimitExceededException.class, () -> TransferPolicy.assertAllowed(snapshot));
    }

    @Test
    void pol04AllowsWhenUnderRateLimit() {
        TransferPolicySnapshot snapshot = snapshot(
                "10.00",
                daytime(),
                "0.00",
                4
        );
        assertDoesNotThrow(() -> TransferPolicy.assertAllowed(snapshot));
    }

    private static TransferPolicySnapshot snapshot(
            String amount,
            Instant occurredAt,
            String spentToday,
            int transfersInLastMinute
    ) {
        return TransferPolicySnapshot.of(
                Money.ofTransfer(new BigDecimal(amount)),
                occurredAt,
                Money.ofBalance(new BigDecimal(spentToday)),
                transfersInLastMinute
        );
    }

    private static Instant daytime() {
        return LocalDateTime.of(2026, 8, 13, 15, 0)
                .atZone(TransferPolicySnapshot.ZONE)
                .toInstant();
    }

    private static Instant nighttime() {
        return LocalDateTime.of(2026, 8, 13, 23, 0)
                .atZone(TransferPolicySnapshot.ZONE)
                .toInstant();
    }
}
