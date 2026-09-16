package dev.hambacon.bank.adapter.out.integration;

import dev.hambacon.bank.application.port.out.ExternalSettlementPort;
import dev.hambacon.bank.domain.Transfer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MockExternalSettlementAdapter implements ExternalSettlementPort {
    private final AtomicReference<Result> nextResult = new AtomicReference<>(Result.SUCCESS);
    private final Set<UUID> settledTransfers = ConcurrentHashMap.newKeySet();
    private final AtomicInteger attemptCount = new AtomicInteger();

    @Override
    public Result settle(Transfer transfer) {
        attemptCount.incrementAndGet();
        if (transfer != null && settledTransfers.contains(transfer.id())) {
            return Result.SUCCESS;
        }
        var result = nextResult.getAndSet(Result.SUCCESS);
        if (result == Result.SUCCESS && transfer != null) {
            settledTransfers.add(transfer.id());
        }
        return result;
    }

    public void failNextRetryably() {
        nextResult.set(Result.RETRYABLE_FAILURE);
    }

    public void failNextPermanently() {
        nextResult.set(Result.PERMANENT_FAILURE);
    }

    public int attemptCount() {
        return attemptCount.get();
    }

    public void reset() {
        nextResult.set(Result.SUCCESS);
        settledTransfers.clear();
        attemptCount.set(0);
    }
}
