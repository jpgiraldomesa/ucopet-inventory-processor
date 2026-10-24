package com.uco.ucopetinventory.domain.stock;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "stocks", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "headquarter_id"}))
public class StockDomain {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "headquarter_id", nullable = false, updatable = false)
    private UUID headquarterId;

    @Column(nullable = false)
    private Integer quantity;

    protected StockDomain() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public UUID getHeadquarterId() {
        return headquarterId;
    }

    public Integer getQuantity() {
        return quantity;
    }
}
