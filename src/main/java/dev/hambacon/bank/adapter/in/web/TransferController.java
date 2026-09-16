package dev.hambacon.bank.adapter.in.web;

import dev.hambacon.bank.application.port.in.TransferUseCase;
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
import java.util.UUID;

import static dev.hambacon.bank.adapter.in.web.ApiDtos.CreateTransferRequest;
import static dev.hambacon.bank.adapter.in.web.ApiDtos.TransferResponse;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
    private final TransferUseCase transferUseCase;

    public TransferController(TransferUseCase transferUseCase) {
        this.transferUseCase = transferUseCase;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreateTransferRequest request
    ) {
        var transfer = transferUseCase.acceptTransfer(
                request.sourceAccountId(), request.destinationAccountId(), request.amountMinor(), key);
        return ResponseEntity.accepted()
                .location(URI.create("/api/transfers/" + transfer.id()))
                .body(TransferResponse.from(transfer));
    }

    @GetMapping("/{transferId}")
    public TransferResponse get(@PathVariable UUID transferId) {
        return TransferResponse.from(transferUseCase.getTransfer(transferId));
    }
}
