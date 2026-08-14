package dev.matheus.payment.adapter.in.messaging.rabbit;

import dev.matheus.payment.adapter.out.persistence.entity.TransferCompletedPayload;
import dev.matheus.payment.application.dto.EventEnvelope;
import dev.matheus.payment.application.port.out.NotifierPort;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferNotificationConsumer {

    private final NotifierPort notifierPort;

    /**
     * Re-delivery is safe: notifying the payee again does not move money.
     * No consumption-idempotency table in this recorte (at-least-once).
     */
    @RabbitListener(queues = "${payment.messaging.queue}")
    public void onTransferCompleted(EventEnvelope<TransferCompletedPayload> envelope) {
        notifierPort.notify(envelope.payload());
    }
}
