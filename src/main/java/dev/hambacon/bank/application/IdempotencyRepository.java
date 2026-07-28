package dev.hambacon.bank.application;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository {
    Optional<Record> find(String key);
    boolean tryInsert(String key, String requestHash, String resourceType, UUID resourceId, String status);
    void updateStatus(String key, String status);
    void updateStatusForResource(UUID resourceId, String status);

    record Record(String key, String requestHash, String resourceType, UUID resourceId, String status,
                  OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
}

