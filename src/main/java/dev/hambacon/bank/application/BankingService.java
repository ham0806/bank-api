package dev.hambacon.bank.application;

import dev.hambacon.bank.domain.Account;
import dev.hambacon.bank.domain.LedgerLine;
import dev.hambacon.bank.domain.Money;
import dev.hambacon.bank.domain.Transfer;
import dev.hambacon.bank.domain.TransferStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BankingService {
    private static final UUID SYSTEM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;

    public BankingService(AccountRepository accountRepository, LedgerRepository ledgerRepository,
                          IdempotencyRepository idempotencyRepository, TransferRepository transferRepository,
                          OutboxRepository outboxRepository) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public Account createAccount(String accountNumber) {
        return accountRepository.create(accountNumber);
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("口座が見つかりません: " + accountId));
    }

    @Transactional(readOnly = true)
    public List<LedgerRepository.TransactionView> getTransactions(UUID accountId) {
        getAccount(accountId);
        return ledgerRepository.findByAccountId(accountId);
    }

    @Transactional
    public OperationResult deposit(UUID accountId, Money amount, String key) {
        requireKey(key);
        var resourceId = UUID.randomUUID();
        var hash = RequestHasher.sha256("deposit|" + accountId + "|" + amount.minor());
        var existing = reserveIdempotency(key, hash, "DEPOSIT", resourceId, "COMPLETED");
        if (existing != null) {
            return new OperationResult(existing.resourceId(), accountId, amount, getAccount(accountId).balanceMinor());
        }
        var account = accountRepository.findByIdForUpdate(accountId);
        requireCustomerAccount(account);
        var system = accountRepository.findByIdForUpdate(SYSTEM_ACCOUNT_ID);
        var updated = account.deposit(amount);
        var updatedSystem = system.applyLedgerDelta(-amount.minor());
        accountRepository.update(updated);
        accountRepository.update(updatedSystem);
        ledgerRepository.post(resourceId, "DEPOSIT", List.of(
                new LedgerLine(accountId, amount.minor()), new LedgerLine(SYSTEM_ACCOUNT_ID, -amount.minor())));
        return new OperationResult(resourceId, accountId, amount, updated.balanceMinor());
    }

    @Transactional
    public OperationResult withdraw(UUID accountId, Money amount, String key) {
        requireKey(key);
        var resourceId = UUID.randomUUID();
        var hash = RequestHasher.sha256("withdraw|" + accountId + "|" + amount.minor());
        var existing = reserveIdempotency(key, hash, "WITHDRAWAL", resourceId, "COMPLETED");
        if (existing != null) {
            return new OperationResult(existing.resourceId(), accountId, amount, getAccount(accountId).balanceMinor());
        }
        var account = accountRepository.findByIdForUpdate(accountId);
        requireCustomerAccount(account);
        var system = accountRepository.findByIdForUpdate(SYSTEM_ACCOUNT_ID);
        var updated = account.withdraw(amount);
        var updatedSystem = system.applyLedgerDelta(amount.minor());
        accountRepository.update(updated);
        accountRepository.update(updatedSystem);
        ledgerRepository.post(resourceId, "WITHDRAWAL", List.of(
                new LedgerLine(accountId, -amount.minor()), new LedgerLine(SYSTEM_ACCOUNT_ID, amount.minor())));
        return new OperationResult(resourceId, accountId, amount, updated.balanceMinor());
    }

    @Transactional
    public Transfer acceptTransfer(UUID sourceAccountId, UUID destinationAccountId, Money amount, String key) {
        requireKey(key);
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new BankingException("送金元と送金先は異なる口座でなければなりません");
        }
        var transferId = UUID.randomUUID();
        var hash = RequestHasher.sha256("transfer|" + sourceAccountId + "|" + destinationAccountId + "|" + amount.minor());
        var existing = reserveIdempotency(key, hash, "TRANSFER", transferId, "PENDING");
        if (existing != null) {
            return transferRepository.findById(existing.resourceId())
                    .orElseThrow(() -> new ResourceNotFoundException("冪等性レコードの振込が見つかりません"));
        }
        lockAccountsInOrder(sourceAccountId, destinationAccountId);
        var source = accountRepository.findByIdForUpdate(sourceAccountId);
        var destination = accountRepository.findByIdForUpdate(destinationAccountId);
        requireCustomerAccount(source);
        requireCustomerAccount(destination);
        accountRepository.update(source.reserve(amount));
        var transfer = new Transfer(transferId, sourceAccountId, destinationAccountId, amount, TransferStatus.PENDING);
        transferRepository.insert(transfer);
        outboxRepository.insert(transferId, "TRANSFER_SETTLEMENT", transferId.toString());
        return transfer;
    }

    @Transactional(readOnly = true)
    public Transfer getTransfer(UUID transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("振込が見つかりません: " + transferId));
    }

    @Transactional
    public void completeTransfer(UUID transferId) {
        var transfer = transferRepository.findByIdForUpdate(transferId);
        if (transfer.status() != TransferStatus.PENDING) return;
        lockAccountsInOrder(transfer.sourceAccountId(), transfer.destinationAccountId());
        var source = accountRepository.findByIdForUpdate(transfer.sourceAccountId());
        var destination = accountRepository.findByIdForUpdate(transfer.destinationAccountId());
        accountRepository.update(source.settleReservedDebit(transfer.amount()));
        accountRepository.update(destination.deposit(transfer.amount()));
        ledgerRepository.post(transfer.id(), "TRANSFER", List.of(
                new LedgerLine(transfer.sourceAccountId(), -transfer.amount().minor()),
                new LedgerLine(transfer.destinationAccountId(), transfer.amount().minor())));
        transferRepository.update(transfer.complete());
        idempotencyRepository.updateStatusForResource(transfer.id(), "COMPLETED");
    }

    @Transactional
    public void failTransfer(UUID transferId) {
        var transfer = transferRepository.findByIdForUpdate(transferId);
        if (transfer.status() != TransferStatus.PENDING) return;
        var source = accountRepository.findByIdForUpdate(transfer.sourceAccountId());
        accountRepository.update(source.releaseReservation(transfer.amount()));
        transferRepository.update(transfer.fail());
        idempotencyRepository.updateStatusForResource(transfer.id(), "FAILED");
    }

    private IdempotencyRepository.Record reserveIdempotency(String key, String hash, String resourceType,
                                                              UUID resourceId, String status) {
        if (idempotencyRepository.tryInsert(key, hash, resourceType, resourceId, status)) return null;
        var existing = idempotencyRepository.find(key)
                .orElseThrow(() -> new IllegalStateException("冪等性レコードを取得できません"));
        if (!existing.requestHash().equals(hash)) {
            throw new IdempotencyConflictException("同じIdempotency-Keyに異なるリクエストは指定できません");
        }
        return existing;
    }

    private void lockAccountsInOrder(UUID first, UUID second) {
        if (first.compareTo(second) > 0) {
            accountRepository.findByIdForUpdate(second);
            accountRepository.findByIdForUpdate(first);
        } else {
            accountRepository.findByIdForUpdate(first);
            accountRepository.findByIdForUpdate(second);
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 255) {
            throw new BankingException("Idempotency-Keyは必須です");
        }
    }

    private static void requireCustomerAccount(Account account) {
        if (account.status() != dev.hambacon.bank.domain.AccountStatus.ACTIVE) {
            throw new BankingException("システム口座は顧客向け操作に利用できません");
        }
    }

    public record OperationResult(UUID transactionId, UUID accountId, Money amount, long balanceMinor) {}
}
