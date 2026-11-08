package com.uco.ucopetinventory.repository.transfer;

import com.uco.ucopetinventory.domain.transfer.TransferDomain;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ITransferRepository extends JpaRepository<TransferDomain, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TransferDomain> findLockedById(UUID id);
}