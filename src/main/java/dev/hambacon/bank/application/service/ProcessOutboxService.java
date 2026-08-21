package dev.hambacon.bank.application.service;

import dev.hambacon.bank.application.ResourceNotFoundException;
import dev.hambacon.bank.application.port.in.ProcessOutboxUseCase;
import dev.hambacon.bank.application.port.in.SettleTransferUseCase;
import dev.hambacon.bank.application.port.out.ExternalSettlementPort;
import dev.hambacon.bank.application.port.out.OutboxRepository;
import dev.hambacon.bank.application.port.out.TransferRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class ProcessOutboxService implements ProcessOutboxUseCase {
    static final int MAX_ATTEMPTS = 3;

    private final OutboxRepository outboxRepository;
    private final TransferRepository transferRepository;
    private final ExternalSettlementPort externalSettlementPort;
    private final SettleTransferUseCase settleTransferUseCase;
    private final Clock clock;

    public ProcessOutboxService(
            OutboxRepository outboxRepository,
            TransferRepository transferRepository,
            ExternalSettlementPort externalSettlementPort,
            SettleTransferUseCase settleTransferUseCase,
            Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.transferRepository = transferRepository;
        this.externalSettlementPort = externalSettlementPort;
        this.settleTransferUseCase = settleTransferUseCase;
        this.clock = clock;
    }

    @Override
    public boolean processOne() {
        var now = OffsetDateTime.now(clock);
        var event = outboxRepository.claimNext(now, now.plusMinutes(1));
        if (event.isEmpty()) {
            return false;
        }
        var claimed = event.get();
        var transfer = transferRepository.findById(claimed.aggregateId())
                .orElseThrow(() -> new ResourceNotFoundException("振込が見つかりません: " + claimed.aggregateId()));
        var result = externalSettlementPort.settle(transfer);
        if (result == ExternalSettlementPort.Result.SUCCESS) {
            settleTransferUseCase.completeTransfer(transfer.id());
            outboxRepository.markDone(claimed.id());
        } else if (result == ExternalSettlementPort.Result.RETRYABLE_FAILURE && claimed.attempts() + 1 < MAX_ATTEMPTS) {
            outboxRepository.scheduleRetry(claimed.id(), claimed.attempts() + 1, now.plusSeconds(1), "外部連携の一時失敗");
        } else {
            settleTransferUseCase.failTransfer(transfer.id());
            outboxRepository.markFailed(claimed.id(), "外部連携に失敗しました");
        }
        return true;
    }
}
