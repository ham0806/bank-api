package dev.hambacon.bank.application.service;

import dev.hambacon.bank.application.port.in.SettleTransferUseCase;
import dev.hambacon.bank.application.port.out.ExternalSettlementPort;
import dev.hambacon.bank.application.port.out.OutboxRepository;
import dev.hambacon.bank.application.port.out.TransferRepository;
import dev.hambacon.bank.domain.Transfer;
import dev.hambacon.bank.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessOutboxServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-21T04:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);

    @Mock private OutboxRepository outboxRepository;
    @Mock private TransferRepository transferRepository;
    @Mock private ExternalSettlementPort externalSettlementPort;
    @Mock private SettleTransferUseCase settleTransferUseCase;

    private ProcessOutboxService processOutboxService;

    @BeforeEach
    void setUp() {
        processOutboxService = new ProcessOutboxService(
                outboxRepository, transferRepository, externalSettlementPort, settleTransferUseCase, CLOCK);
    }

    @Test
    void 処理対象がなければfalseを返す() {
        when(outboxRepository.claimNext(NOW, NOW.plusMinutes(1))).thenReturn(Optional.empty());

        assertThat(processOutboxService.processOne()).isFalse();
        verifyNoInteractions(transferRepository, externalSettlementPort, settleTransferUseCase);
    }

    @Test
    void 外部連携が成功したら振込を完了してOutboxをDONEにする() {
        var transfer = pendingTransfer();
        var event = event(transfer.id(), 0);
        when(outboxRepository.claimNext(NOW, NOW.plusMinutes(1))).thenReturn(Optional.of(event));
        when(transferRepository.findById(transfer.id())).thenReturn(Optional.of(transfer));
        when(externalSettlementPort.settle(transfer)).thenReturn(ExternalSettlementPort.Result.SUCCESS);

        assertThat(processOutboxService.processOne()).isTrue();

        verify(settleTransferUseCase).completeTransfer(transfer.id());
        verify(outboxRepository).markDone(event.id());
    }

    @Test
    void 一時失敗かつ再試行回数内なら予約を残してリトライする() {
        var transfer = pendingTransfer();
        var event = event(transfer.id(), 0);
        when(outboxRepository.claimNext(NOW, NOW.plusMinutes(1))).thenReturn(Optional.of(event));
        when(transferRepository.findById(transfer.id())).thenReturn(Optional.of(transfer));
        when(externalSettlementPort.settle(transfer)).thenReturn(ExternalSettlementPort.Result.RETRYABLE_FAILURE);

        assertThat(processOutboxService.processOne()).isTrue();

        verify(outboxRepository).scheduleRetry(event.id(), 1, NOW.plusSeconds(1), "外部連携の一時失敗");
        verifyNoInteractions(settleTransferUseCase);
    }

    @Test
    void 恒久失敗または再試行上限なら振込を失敗させて予約を解除する() {
        var transfer = pendingTransfer();
        var event = event(transfer.id(), 2);
        when(outboxRepository.claimNext(NOW, NOW.plusMinutes(1))).thenReturn(Optional.of(event));
        when(transferRepository.findById(transfer.id())).thenReturn(Optional.of(transfer));
        when(externalSettlementPort.settle(transfer)).thenReturn(ExternalSettlementPort.Result.RETRYABLE_FAILURE);

        assertThat(processOutboxService.processOne()).isTrue();

        verify(settleTransferUseCase).failTransfer(transfer.id());
        verify(outboxRepository).markFailed(event.id(), "外部連携に失敗しました");
    }

    private static Transfer pendingTransfer() {
        return new Transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 600, TransferStatus.PENDING);
    }

    private static OutboxRepository.Event event(UUID transferId, int attempts) {
        return new OutboxRepository.Event(UUID.randomUUID(), transferId, "TRANSFER_SETTLEMENT", attempts, transferId.toString());
    }
}
