package dev.hambacon.bank.application;

import dev.hambacon.bank.domain.Transfer;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository {
    void insert(Transfer transfer);
    Optional<Transfer> findById(UUID transferId);
    Transfer findByIdForUpdate(UUID transferId);
    void update(Transfer transfer);
}

