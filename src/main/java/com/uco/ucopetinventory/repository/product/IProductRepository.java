package com.uco.ucopetinventory.repository.product;

import com.uco.ucopetinventory.domain.product.ProductDomain;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface IProductRepository extends JpaRepository<ProductDomain, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductDomain> findLockedById(UUID id);
}
