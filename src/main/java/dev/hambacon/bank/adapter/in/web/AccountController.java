package dev.hambacon.bank.adapter.in.web;

import dev.hambacon.bank.application.port.in.AccountUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static dev.hambacon.bank.adapter.in.web.ApiDtos.AccountResponse;
import static dev.hambacon.bank.adapter.in.web.ApiDtos.AmountRequest;
import static dev.hambacon.bank.adapter.in.web.ApiDtos.CreateAccountRequest;
import static dev.hambacon.bank.adapter.in.web.ApiDtos.OperationResponse;
import static dev.hambacon.bank.adapter.in.web.ApiDtos.TransactionResponse;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountUseCase accountUseCase;

    public AccountController(AccountUseCase accountUseCase) {
        this.accountUseCase = accountUseCase;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        var account = accountUseCase.createAccount(request.accountNumber());
        return ResponseEntity.created(URI.create("/api/accounts/" + account.id())).body(AccountResponse.from(account));
    }

    @GetMapping("/{accountId}")
    public AccountResponse get(@PathVariable UUID accountId) {
        return AccountResponse.from(accountUseCase.getAccount(accountId));
    }

    @GetMapping("/{accountId}/transactions")
    public List<TransactionResponse> transactions(@PathVariable UUID accountId) {
        return accountUseCase.getTransactions(accountId).stream().map(TransactionResponse::from).toList();
    }

    @PostMapping("/{accountId}/deposits")
    public ResponseEntity<OperationResponse> deposit(
            @PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AmountRequest request
    ) {
        return ResponseEntity.status(201).body(OperationResponse.from(
                accountUseCase.deposit(accountId, request.amountMinor(), key)));
    }

    @PostMapping("/{accountId}/withdrawals")
    public ResponseEntity<OperationResponse> withdraw(
            @PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AmountRequest request
    ) {
        return ResponseEntity.status(201).body(OperationResponse.from(
                accountUseCase.withdraw(accountId, request.amountMinor(), key)));
    }
}
