package dev.matheus.payment.application.port.out;

import dev.matheus.payment.adapter.out.persistence.entity.TransferCompletedPayload;

public interface NotifierPort {

    void notify(TransferCompletedPayload payload);
}
