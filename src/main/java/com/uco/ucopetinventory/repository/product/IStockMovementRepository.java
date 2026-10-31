package com.uco.ucopetinventory.repository.product;

import com.uco.ucopetinventory.domain.product.StockMovementDomain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IStockMovementRepository extends JpaRepository<StockMovementDomain, UUID> {

    boolean existsByProductIdAndHeadquarterId(UUID productId, UUID headquarterId);
}
