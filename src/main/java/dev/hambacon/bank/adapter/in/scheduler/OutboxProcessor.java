package dev.hambacon.bank.adapter.in.scheduler;

import dev.hambacon.bank.application.port.in.ProcessOutboxUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxProcessor {
    private final ProcessOutboxUseCase processOutboxUseCase;
    private final boolean schedulerEnabled;

    public OutboxProcessor(
            ProcessOutboxUseCase processOutboxUseCase,
            @Value("${bank.outbox.scheduler-enabled:false}") boolean schedulerEnabled
    ) {
        this.processOutboxUseCase = processOutboxUseCase;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(fixedDelayString = "${bank.outbox.poll-interval-ms:1000}")
    public void scheduledProcess() {
        if (!schedulerEnabled) {
            return;
        }
        processOutboxUseCase.processOne();
    }

    public boolean processOne() {
        return processOutboxUseCase.processOne();
    }
}
