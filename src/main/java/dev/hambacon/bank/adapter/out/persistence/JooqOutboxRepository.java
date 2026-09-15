package dev.hambacon.bank.adapter.out.persistence;

import dev.hambacon.bank.adapter.out.persistence.jooq.tables.records.OutboxEventsRecord;
import dev.hambacon.bank.application.OutboxRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static dev.hambacon.bank.adapter.out.persistence.jooq.Tables.OUTBOX_EVENTS;
import static org.jooq.impl.DSL.noCondition;

@Repository
public class JooqOutboxRepository implements OutboxRepository {
    private final DSLContext dsl;
    private final Clock clock;

    public JooqOutboxRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public void insert(UUID aggregateId, String eventType, String payload) {
        var now = OffsetDateTime.now(clock);
        dsl.insertInto(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.ID, UUID.randomUUID())
                .set(OUTBOX_EVENTS.AGGREGATE_ID, aggregateId)
                .set(OUTBOX_EVENTS.EVENT_TYPE, eventType)
                .set(OUTBOX_EVENTS.PAYLOAD, payload)
                .set(OUTBOX_EVENTS.STATUS, "PENDING")
                .set(OUTBOX_EVENTS.ATTEMPTS, 0)
                .set(OUTBOX_EVENTS.AVAILABLE_AT, now)
                .set(OUTBOX_EVENTS.CREATED_AT, now)
                .set(OUTBOX_EVENTS.UPDATED_AT, now)
                .execute();
    }

    @Override
    @Transactional
    public Optional<Event> claimNext(OffsetDateTime now, OffsetDateTime lockUntil) {
        Condition pending = OUTBOX_EVENTS.STATUS.eq("PENDING").and(OUTBOX_EVENTS.AVAILABLE_AT.le(now));
        Condition expired = OUTBOX_EVENTS.STATUS.eq("PROCESSING")
                .and(OUTBOX_EVENTS.LOCKED_UNTIL.isNull().or(OUTBOX_EVENTS.LOCKED_UNTIL.le(now)));
        var record = dsl.selectFrom(OUTBOX_EVENTS)
                .where(pending.or(expired))
                .orderBy(OUTBOX_EVENTS.AVAILABLE_AT.asc())
                .limit(1)
                .forUpdate()
                .skipLocked()
                .fetchOne();
        if (record == null) return Optional.empty();
        dsl.update(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.STATUS, "PROCESSING")
                .set(OUTBOX_EVENTS.LOCKED_UNTIL, lockUntil)
                .set(OUTBOX_EVENTS.UPDATED_AT, now)
                .where(OUTBOX_EVENTS.ID.eq(record.getId()))
                .execute();
        return Optional.of(new Event(record.getId(), record.getAggregateId(), record.getEventType(),
                record.getAttempts(), record.getPayload()));
    }

    @Override
    public void markDone(UUID eventId) {
        dsl.update(OUTBOX_EVENTS).set(OUTBOX_EVENTS.STATUS, "DONE")
                .set(OUTBOX_EVENTS.LOCKED_UNTIL, (OffsetDateTime) null)
                .set(OUTBOX_EVENTS.UPDATED_AT, OffsetDateTime.now(clock))
                .where(OUTBOX_EVENTS.ID.eq(eventId)).execute();
    }

    @Override
    public void scheduleRetry(UUID eventId, int attempts, OffsetDateTime availableAt, String error) {
        dsl.update(OUTBOX_EVENTS).set(OUTBOX_EVENTS.STATUS, "PENDING")
                .set(OUTBOX_EVENTS.ATTEMPTS, attempts)
                .set(OUTBOX_EVENTS.AVAILABLE_AT, availableAt)
                .set(OUTBOX_EVENTS.LAST_ERROR, error)
                .set(OUTBOX_EVENTS.LOCKED_UNTIL, (OffsetDateTime) null)
                .set(OUTBOX_EVENTS.UPDATED_AT, OffsetDateTime.now(clock))
                .where(OUTBOX_EVENTS.ID.eq(eventId)).execute();
    }

    @Override
    public void markFailed(UUID eventId, String error) {
        dsl.update(OUTBOX_EVENTS).set(OUTBOX_EVENTS.STATUS, "FAILED")
                .set(OUTBOX_EVENTS.LAST_ERROR, error)
                .set(OUTBOX_EVENTS.LOCKED_UNTIL, (OffsetDateTime) null)
                .set(OUTBOX_EVENTS.UPDATED_AT, OffsetDateTime.now(clock))
                .where(OUTBOX_EVENTS.ID.eq(eventId)).execute();
    }
}
