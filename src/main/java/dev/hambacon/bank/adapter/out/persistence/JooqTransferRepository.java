package dev.hambacon.bank.adapter.out.persistence;

import dev.hambacon.bank.adapter.out.persistence.jooq.tables.records.TransfersRecord;
import dev.hambacon.bank.application.ResourceNotFoundException;
import dev.hambacon.bank.application.port.out.TransferRepository;
import dev.hambacon.bank.domain.Transfer;
import dev.hambacon.bank.domain.TransferStatus;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static dev.hambacon.bank.adapter.out.persistence.jooq.Tables.TRANSFERS;

@Repository
public class JooqTransferRepository implements TransferRepository {
    private final DSLContext dsl;
    private final Clock clock;

    public JooqTransferRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public void insert(Transfer transfer) {
        var now = OffsetDateTime.now(clock);
        dsl.insertInto(TRANSFERS)
                .set(TRANSFERS.ID, transfer.id())
                .set(TRANSFERS.SOURCE_ACCOUNT_ID, transfer.sourceAccountId())
                .set(TRANSFERS.DESTINATION_ACCOUNT_ID, transfer.destinationAccountId())
                .set(TRANSFERS.AMOUNT_MINOR, transfer.amountMinor())
                .set(TRANSFERS.STATUS, transfer.status().name())
                .set(TRANSFERS.CREATED_AT, now)
                .set(TRANSFERS.UPDATED_AT, now)
                .execute();
    }

    @Override
    public Optional<Transfer> findById(UUID transferId) {
        return Optional.ofNullable(dsl.selectFrom(TRANSFERS).where(TRANSFERS.ID.eq(transferId)).fetchOne()).map(this::toDomain);
    }

    @Override
    public Transfer findByIdForUpdate(UUID transferId) {
        var record = dsl.selectFrom(TRANSFERS).where(TRANSFERS.ID.eq(transferId)).forUpdate().fetchOne();
        if (record == null) throw new ResourceNotFoundException("振込が見つかりません: " + transferId);
        return toDomain(record);
    }

    @Override
    public void update(Transfer transfer) {
        dsl.update(TRANSFERS).set(TRANSFERS.STATUS, transfer.status().name())
                .set(TRANSFERS.UPDATED_AT, OffsetDateTime.now(clock))
                .where(TRANSFERS.ID.eq(transfer.id())).execute();
    }

    private Transfer toDomain(TransfersRecord record) {
        return new Transfer(record.getId(), record.getSourceAccountId(), record.getDestinationAccountId(),
                record.getAmountMinor(), TransferStatus.valueOf(record.getStatus()));
    }
}
