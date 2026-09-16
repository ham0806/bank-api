package dev.hambacon.bank.adapter.out.persistence;

import dev.hambacon.bank.application.port.out.IdempotencyRepository;
import dev.hambacon.bank.adapter.out.persistence.jooq.tables.records.IdempotencyRecordsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static dev.hambacon.bank.adapter.out.persistence.jooq.Tables.IDEMPOTENCY_RECORDS;

@Repository
public class JooqIdempotencyRepository implements IdempotencyRepository {
    private final DSLContext dsl;
    private final Clock clock;

    public JooqIdempotencyRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public Optional<Record> find(String key) {
        return Optional.ofNullable(dsl.selectFrom(IDEMPOTENCY_RECORDS)
                        .where(IDEMPOTENCY_RECORDS.IDEMPOTENCY_KEY.eq(key)).fetchOne())
                .map(this::toDomain);
    }

    @Override
    public boolean tryInsert(String key, String requestHash, String resourceType, UUID resourceId, String status) {
        var now = OffsetDateTime.now(clock);
        return dsl.insertInto(IDEMPOTENCY_RECORDS)
                .set(IDEMPOTENCY_RECORDS.IDEMPOTENCY_KEY, key)
                .set(IDEMPOTENCY_RECORDS.REQUEST_HASH, requestHash)
                .set(IDEMPOTENCY_RECORDS.RESOURCE_TYPE, resourceType)
                .set(IDEMPOTENCY_RECORDS.RESOURCE_ID, resourceId)
                .set(IDEMPOTENCY_RECORDS.STATUS, status)
                .set(IDEMPOTENCY_RECORDS.CREATED_AT, now)
                .set(IDEMPOTENCY_RECORDS.UPDATED_AT, now)
                .onConflict(IDEMPOTENCY_RECORDS.IDEMPOTENCY_KEY).doNothing()
                .execute() == 1;
    }

    @Override
    public void updateStatus(String key, String status) {
        dsl.update(IDEMPOTENCY_RECORDS).set(IDEMPOTENCY_RECORDS.STATUS, status)
                .set(IDEMPOTENCY_RECORDS.UPDATED_AT, OffsetDateTime.now(clock))
                .where(IDEMPOTENCY_RECORDS.IDEMPOTENCY_KEY.eq(key)).execute();
    }

    @Override
    public void updateStatusForResource(UUID resourceId, String status) {
        dsl.update(IDEMPOTENCY_RECORDS).set(IDEMPOTENCY_RECORDS.STATUS, status)
                .set(IDEMPOTENCY_RECORDS.UPDATED_AT, OffsetDateTime.now(clock))
                .where(IDEMPOTENCY_RECORDS.RESOURCE_ID.eq(resourceId)).execute();
    }

    private Record toDomain(IdempotencyRecordsRecord record) {
        return new Record(record.getIdempotencyKey(), record.getRequestHash(), record.getResourceType(),
                record.getResourceId(), record.getStatus(), record.getCreatedAt(), record.getUpdatedAt());
    }
}

