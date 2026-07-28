package dev.hambacon.bank.adapter.out.persistence;

import dev.hambacon.bank.application.LedgerRepository;
import dev.hambacon.bank.domain.LedgerLine;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static dev.hambacon.bank.adapter.out.persistence.jooq.Tables.LEDGER_ENTRIES;
import static dev.hambacon.bank.adapter.out.persistence.jooq.Tables.LEDGER_TRANSACTIONS;

@Repository
public class JooqLedgerRepository implements LedgerRepository {
    private final DSLContext dsl;
    private final Clock clock;

    public JooqLedgerRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public UUID post(UUID referenceId, String kind, List<LedgerLine> lines) {
        if (lines.stream().mapToLong(LedgerLine::amountMinor).sum() != 0) {
            throw new IllegalArgumentException("仕訳の貸借合計が一致しません");
        }
        // 業務操作IDを仕訳トランザクションIDにも使い、冪等再送時に同じ仕訳を参照できるようにする。
        var transactionId = referenceId;
        var now = OffsetDateTime.now(clock);
        dsl.insertInto(LEDGER_TRANSACTIONS)
                .set(LEDGER_TRANSACTIONS.ID, transactionId)
                .set(LEDGER_TRANSACTIONS.REFERENCE_ID, referenceId)
                .set(LEDGER_TRANSACTIONS.KIND, kind)
                .set(LEDGER_TRANSACTIONS.CREATED_AT, now)
                .execute();
        for (var line : lines) {
            dsl.insertInto(LEDGER_ENTRIES)
                    .set(LEDGER_ENTRIES.ID, UUID.randomUUID())
                    .set(LEDGER_ENTRIES.TRANSACTION_ID, transactionId)
                    .set(LEDGER_ENTRIES.ACCOUNT_ID, line.accountId())
                    .set(LEDGER_ENTRIES.AMOUNT_MINOR, line.amountMinor())
                    .set(LEDGER_ENTRIES.CREATED_AT, now)
                    .execute();
        }
        return transactionId;
    }

    @Override
    public List<TransactionView> findByAccountId(UUID accountId) {
        var records = dsl.select(
                        LEDGER_TRANSACTIONS.ID,
                        LEDGER_TRANSACTIONS.REFERENCE_ID,
                        LEDGER_TRANSACTIONS.KIND,
                        LEDGER_ENTRIES.AMOUNT_MINOR,
                        LEDGER_TRANSACTIONS.CREATED_AT)
                .from(LEDGER_ENTRIES)
                .join(LEDGER_TRANSACTIONS).on(LEDGER_TRANSACTIONS.ID.eq(LEDGER_ENTRIES.TRANSACTION_ID))
                .where(LEDGER_ENTRIES.ACCOUNT_ID.eq(accountId))
                .orderBy(LEDGER_TRANSACTIONS.CREATED_AT.desc())
                .fetch();
        var result = new ArrayList<TransactionView>();
        for (var record : records) {
            result.add(new TransactionView(record.get(LEDGER_TRANSACTIONS.ID), record.get(LEDGER_TRANSACTIONS.REFERENCE_ID),
                    record.get(LEDGER_TRANSACTIONS.KIND), record.get(LEDGER_ENTRIES.AMOUNT_MINOR),
                    record.get(LEDGER_TRANSACTIONS.CREATED_AT)));
        }
        return result;
    }
}
