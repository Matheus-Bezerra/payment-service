package dev.matheus.payment.domain.model;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public final class TransferPolicySnapshot {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final Money amount;
    private final Instant occurredAt;
    private final ZoneId zone;
    private final Money payerSpentToday;
    private final int payerTransfersInLastMinute;

    public TransferPolicySnapshot(
            Money amount,
            Instant occurredAt,
            ZoneId zone,
            Money payerSpentToday,
            int payerTransfersInLastMinute
    ) {
        this.amount = Objects.requireNonNull(amount, "amount is required");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        this.zone = Objects.requireNonNull(zone, "zone is required");
        this.payerSpentToday = Objects.requireNonNull(payerSpentToday, "payerSpentToday is required");
        if (payerTransfersInLastMinute < 0) {
            throw new IllegalArgumentException("payerTransfersInLastMinute cannot be negative");
        }
        this.payerTransfersInLastMinute = payerTransfersInLastMinute;
    }

    public static TransferPolicySnapshot of(
            Money amount,
            Instant occurredAt,
            Money payerSpentToday,
            int payerTransfersInLastMinute
    ) {
        return new TransferPolicySnapshot(
                amount,
                occurredAt,
                ZONE,
                payerSpentToday,
                payerTransfersInLastMinute
        );
    }

    public Money amount() {
        return amount;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public ZoneId zone() {
        return zone;
    }

    public Money payerSpentToday() {
        return payerSpentToday;
    }

    public int payerTransfersInLastMinute() {
        return payerTransfersInLastMinute;
    }
}
