package com.uco.ucopetinventory.repository.stock;

import com.uco.ucopetinventory.domain.stock.StockDomain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IStockRepository extends JpaRepository<StockDomain, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE StockDomain s SET s.quantity = s.quantity + :quantity "
            + "WHERE s.itemId = :productId AND s.headquarterId = :headquarterId "
            + "AND s.quantity + :quantity >= 0")
    int addQuantity(@Param("productId") UUID productId,
                    @Param("headquarterId") UUID headquarterId,
                    @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO stocks (id, product_id, headquarter_id, quantity) "
            + "VALUES (:id, :productId, :headquarterId, :quantity) "
            + "ON CONFLICT (product_id, headquarter_id) DO UPDATE "
            + "SET quantity = stocks.quantity + EXCLUDED.quantity", nativeQuery = true)
    void createOrAddQuantity(@Param("id") UUID id,
                             @Param("productId") UUID productId,
                             @Param("headquarterId") UUID headquarterId,
                             @Param("quantity") int quantity);
}
