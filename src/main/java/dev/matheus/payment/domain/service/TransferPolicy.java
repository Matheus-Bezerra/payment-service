package dev.matheus.payment.domain.service;

import dev.matheus.payment.domain.exception.DailyTransferLimitExceededException;
import dev.matheus.payment.domain.exception.TransferAmountLimitExceededException;
import dev.matheus.payment.domain.exception.TransferRateLimitExceededException;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.TransferPolicySnapshot;
import java.math.BigDecimal;
import java.time.LocalTime;

public final class TransferPolicy {

    public static final Money MAX_TRANSFER_AMOUNT = Money.ofTransfer(new BigDecimal("20000.00"));
    public static final Money NIGHT_MAX_TRANSFER_AMOUNT = Money.ofTransfer(new BigDecimal("5000.00"));
    public static final Money DAILY_LIMIT = Money.ofBalance(new BigDecimal("80000.00"));
    public static final int RATE_LIMIT_MAX = 5;
    public static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    public static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    private TransferPolicy() {
    }

    public static void assertAllowed(TransferPolicySnapshot snapshot) {
        Money amount = snapshot.amount();

        // POL-01
        if (amount.isGreaterThan(MAX_TRANSFER_AMOUNT)) {
            throw new TransferAmountLimitExceededException(
                    "transfer amount exceeds maximum of " + MAX_TRANSFER_AMOUNT.amount()
            );
        }

        // POL-02 — night cap replaces day cap
        if (isNight(snapshot) && amount.isGreaterThan(NIGHT_MAX_TRANSFER_AMOUNT)) {
            throw new TransferAmountLimitExceededException(
                    "transfer amount exceeds night maximum of " + NIGHT_MAX_TRANSFER_AMOUNT.amount()
            );
        }

        // POL-03
        Money projectedDaily = snapshot.payerSpentToday().add(amount);
        if (projectedDaily.isGreaterThan(DAILY_LIMIT)) {
            throw new DailyTransferLimitExceededException(
                    "daily transfer limit of " + DAILY_LIMIT.amount() + " would be exceeded"
            );
        }

        // POL-04
        if (snapshot.payerTransfersInLastMinute() >= RATE_LIMIT_MAX) {
            throw new TransferRateLimitExceededException(
                    "maximum of " + RATE_LIMIT_MAX + " transfers per minute exceeded"
            );
        }
    }

    static boolean isNight(TransferPolicySnapshot snapshot) {
        LocalTime time = snapshot.occurredAt().atZone(snapshot.zone()).toLocalTime();
        return !time.isBefore(NIGHT_START) || time.isBefore(NIGHT_END);
    }
}
