package dev.matheus.payment.application.port.out;

import dev.matheus.payment.domain.event.TransferCompleted;

public interface OutboxPort {

    void save(TransferCompleted event);
}
