package dev.hambacon.bank.adapter.out.persistence;

import dev.hambacon.bank.adapter.out.persistence.jooq.tables.Accounts;
import dev.hambacon.bank.adapter.out.persistence.jooq.tables.records.AccountsRecord;
import dev.hambacon.bank.application.AccountRepository;
import dev.hambacon.bank.application.ResourceNotFoundException;
import dev.hambacon.bank.domain.Account;
import dev.hambacon.bank.domain.AccountStatus;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static dev.hambacon.bank.adapter.out.persistence.jooq.Tables.ACCOUNTS;

@Repository
public class JooqAccountRepository implements AccountRepository {
    private final DSLContext dsl;
    private final Clock clock;

    public JooqAccountRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public Account create(String accountNumber) {
        var now = OffsetDateTime.now(clock);
        var id = UUID.randomUUID();
        dsl.insertInto(ACCOUNTS)
                .set(ACCOUNTS.ID, id)
                .set(ACCOUNTS.ACCOUNT_NUMBER, accountNumber)
                .set(ACCOUNTS.BALANCE_MINOR, 0L)
                .set(ACCOUNTS.RESERVED_MINOR, 0L)
                .set(ACCOUNTS.VERSION, 0L)
                .set(ACCOUNTS.STATUS, AccountStatus.ACTIVE.name())
                .set(ACCOUNTS.CREATED_AT, now)
                .set(ACCOUNTS.UPDATED_AT, now)
                .execute();
        return new Account(id, accountNumber, 0, 0, 0, AccountStatus.ACTIVE);
    }

    @Override
    public Optional<Account> findById(UUID accountId) {
        return Optional.ofNullable(dsl.selectFrom(ACCOUNTS).where(ACCOUNTS.ID.eq(accountId)).fetchOne())
                .map(this::toDomain);
    }

    @Override
    public Account findByIdForUpdate(UUID accountId) {
        var record = dsl.selectFrom(ACCOUNTS)
                .where(ACCOUNTS.ID.eq(accountId))
                .forUpdate()
                .fetchOne();
        if (record == null) throw new ResourceNotFoundException("口座が見つかりません: " + accountId);
        return toDomain(record);
    }

    @Override
    public void update(Account account) {
        var changed = dsl.update(ACCOUNTS)
                .set(ACCOUNTS.BALANCE_MINOR, account.balanceMinor())
                .set(ACCOUNTS.RESERVED_MINOR, account.reservedMinor())
                .set(ACCOUNTS.VERSION, account.version())
                .set(ACCOUNTS.UPDATED_AT, OffsetDateTime.now(clock))
                .where(ACCOUNTS.ID.eq(account.id()))
                .and(ACCOUNTS.VERSION.eq(account.version() - 1))
                .execute();
        if (changed != 1) throw new IllegalStateException("口座更新の競合を検出しました: " + account.id());
    }

    private Account toDomain(AccountsRecord record) {
        return new Account(record.getId(), record.getAccountNumber(), record.getBalanceMinor(),
                record.getReservedMinor(), record.getVersion(), AccountStatus.valueOf(record.getStatus()));
    }
}

