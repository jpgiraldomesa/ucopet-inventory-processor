package com.uco.ucopetinventory.repository.product;

import com.uco.ucopetinventory.domain.product.StockDomain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IStockRepository extends JpaRepository<StockDomain, UUID> {

    Optional<StockDomain> findByProductIdAndHeadquarterId(UUID productId, UUID headquarterId);
}
