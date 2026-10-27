package com.uco.ucopetinventory.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "service_headquarters")
public class ServiceHeadquarterDomain {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    @Column(name = "headquarter_id", nullable = false, updatable = false)
    private UUID headquarterId;

    protected ServiceHeadquarterDomain() {
    }

    public ServiceHeadquarterDomain(final UUID id, final UUID serviceId, final UUID headquarterId) {
        this.id = id;
        this.serviceId = serviceId;
        this.headquarterId = headquarterId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public UUID getHeadquarterId() {
        return headquarterId;
    }
}
