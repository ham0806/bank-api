package dev.hambacon.bank.worker;

import dev.hambacon.bank.application.BankingService;
import dev.hambacon.bank.application.ExternalSettlementPort;
import dev.hambacon.bank.application.OutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.OffsetDateTime;

@Component
public class OutboxProcessor {
    private final OutboxRepository outboxRepository;
    private final BankingService bankingService;
    private final ExternalSettlementPort externalSettlementPort;
    private final Clock clock;
    private final boolean schedulerEnabled;

    public OutboxProcessor(OutboxRepository outboxRepository, BankingService bankingService,
                           ExternalSettlementPort externalSettlementPort, Clock clock,
                           @Value("${bank.outbox.scheduler-enabled:false}") boolean schedulerEnabled) {
        this.outboxRepository = outboxRepository;
        this.bankingService = bankingService;
        this.externalSettlementPort = externalSettlementPort;
        this.clock = clock;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(fixedDelayString = "${bank.outbox.poll-interval-ms:1000}")
    public void scheduledProcess() {
        if (!schedulerEnabled) return;
        processOne();
    }

    public boolean processOne() {
        var now = OffsetDateTime.now(clock);
        var event = outboxRepository.claimNext(now, now.plusMinutes(1));
        if (event.isEmpty()) return false;
        var claimed = event.get();
        var transfer = bankingService.getTransfer(claimed.aggregateId());
        var result = externalSettlementPort.settle(transfer);
        if (result == ExternalSettlementPort.Result.SUCCESS) {
            bankingService.completeTransfer(transfer.id());
            outboxRepository.markDone(claimed.id());
        } else if (result == ExternalSettlementPort.Result.RETRYABLE_FAILURE && claimed.attempts() + 1 < 3) {
            outboxRepository.scheduleRetry(claimed.id(), claimed.attempts() + 1, now.plusSeconds(1), "外部連携の一時失敗");
        } else {
            bankingService.failTransfer(transfer.id());
            outboxRepository.markFailed(claimed.id(), "外部連携に失敗しました");
        }
        return true;
    }
}
