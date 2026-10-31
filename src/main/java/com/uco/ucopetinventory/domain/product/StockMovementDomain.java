package com.uco.ucopetinventory.domain.product;

import com.uco.ucopetinventory.domain.product.enums.StockMovementReason;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_movements")
public class StockMovementDomain {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "headquarter_id", nullable = false, updatable = false)
    private UUID headquarterId;

    @Column(nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private Integer balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private StockMovementReason reason;

    @Column(updatable = false)
    private String note;

    @Column(name = "performed_by", updatable = false)
    private UUID performedBy;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected StockMovementDomain() {
    }

    public StockMovementDomain(final UUID productId, final UUID headquarterId, final Integer quantity,
                               final StockMovementReason reason, final String note, final UUID performedBy) {
        this.productId = productId;
        this.headquarterId = headquarterId;
        this.quantity = quantity;
        this.reason = reason;
        this.note = note;
        this.performedBy = performedBy;
    }

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getHeadquarterId() {
        return headquarterId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Integer getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(final Integer balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public StockMovementReason getReason() {
        return reason;
    }

    public void setReason(final StockMovementReason reason) {
        this.reason = reason;
    }

    public String getNote() {
        return note;
    }

    public UUID getPerformedBy() {
        return performedBy;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(final Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
