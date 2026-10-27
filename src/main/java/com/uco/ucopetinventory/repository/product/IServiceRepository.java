package com.uco.ucopetinventory.repository.product;

import com.uco.ucopetinventory.domain.product.ServiceDomain;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface IServiceRepository extends JpaRepository<ServiceDomain, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ServiceDomain> findLockedById(UUID id);
}
