package dev.hambacon.bank.application;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository {
    void insert(UUID aggregateId, String eventType, String payload);
    Optional<Event> claimNext(OffsetDateTime now, OffsetDateTime lockUntil);
    void markDone(UUID eventId);
    void scheduleRetry(UUID eventId, int attempts, OffsetDateTime availableAt, String error);
    void markFailed(UUID eventId, String error);

    record Event(UUID id, UUID aggregateId, String eventType, int attempts, String payload) {}
}

