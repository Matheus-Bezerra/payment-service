package dev.matheus.payment.adapter.out.time;

import dev.matheus.payment.application.port.out.ClockPort;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SystemClockAdapter implements ClockPort {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
