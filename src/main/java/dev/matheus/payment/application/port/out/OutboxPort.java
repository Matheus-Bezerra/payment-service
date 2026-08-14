package dev.matheus.payment.application.port.out;

import dev.matheus.payment.application.dto.PendingNotification;
import dev.matheus.payment.domain.event.TransferCompleted;
import java.util.List;
import java.util.UUID;

public interface OutboxPort {

    void save(TransferCompleted event);

    List<PendingNotification> claimPending(int batchSize);

    void markPublished(UUID eventId);

    void markFailed(UUID eventId);
}
