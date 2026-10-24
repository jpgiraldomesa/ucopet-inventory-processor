package com.uco.ucopetinventory.domain.catalog;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_headquarters")
public class CatalogItemHeadquarterDomain {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "headquarter_id", nullable = false, updatable = false)
    private UUID headquarterId;

    protected CatalogItemHeadquarterDomain() {
    }

    public CatalogItemHeadquarterDomain(final UUID id, final UUID itemId, final UUID headquarterId) {
        this.id = id;
        this.itemId = itemId;
        this.headquarterId = headquarterId;
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
}
