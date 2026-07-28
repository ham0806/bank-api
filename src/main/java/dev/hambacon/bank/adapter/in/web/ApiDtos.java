package dev.hambacon.bank.adapter.in.web;

import dev.hambacon.bank.application.BankingService;
import dev.hambacon.bank.application.LedgerRepository;
import dev.hambacon.bank.domain.Account;
import dev.hambacon.bank.domain.Transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {}

    public record CreateAccountRequest(
            @NotBlank @Size(max = 32) String accountNumber
    ) {}

    public record AmountRequest(
            @Positive long amountMinor
    ) {}

    public record CreateTransferRequest(
            @NotNull UUID sourceAccountId,
            @NotNull UUID destinationAccountId,
            @Positive long amountMinor
    ) {}

    public record AccountResponse(
            UUID id,
            String accountNumber,
            long balanceMinor,
            long reservedMinor,
            long availableMinor,
            String status
    ) {
        public static AccountResponse from(Account account) {
            return new AccountResponse(account.id(), account.accountNumber(), account.balanceMinor(),
                    account.reservedMinor(), account.availableMinor(), account.status().name());
        }
    }

    public record OperationResponse(
            UUID transactionId,
            UUID accountId,
            long amountMinor,
            long balanceMinor
    ) {
        public static OperationResponse from(BankingService.OperationResult result) {
            return new OperationResponse(result.transactionId(), result.accountId(), result.amountMinor(), result.balanceMinor());
        }
    }

    public record TransferResponse(
            UUID id,
            UUID sourceAccountId,
            UUID destinationAccountId,
            long amountMinor,
            String status
    ) {
        public static TransferResponse from(Transfer transfer) {
            return new TransferResponse(transfer.id(), transfer.sourceAccountId(), transfer.destinationAccountId(),
                    transfer.amountMinor(), transfer.status().name());
        }
    }

    public record TransactionResponse(
            UUID transactionId,
            UUID referenceId,
            String kind,
            long amountMinor,
            OffsetDateTime createdAt
    ) {
        public static TransactionResponse from(LedgerRepository.TransactionView view) {
            return new TransactionResponse(view.transactionId(), view.referenceId(), view.kind(),
                    view.amountMinor(), view.createdAt());
        }
    }
}

