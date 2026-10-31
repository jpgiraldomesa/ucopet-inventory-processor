package com.uco.ucopetinventory.domain.product;

import com.uco.ucopetinventory.domain.product.enums.ServiceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "services")
public class ServiceDomain extends CatalogItemDomain {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ServiceCategory category;

    @Column(nullable = false)
    private Boolean purchasable;

    @Transient
    private List<UUID> headquarterIds;

    protected ServiceDomain() {
    }

    public ServiceCategory getCategory() {
        return category;
    }

    public void setCategory(final ServiceCategory category) {
        this.category = category;
    }

    public Boolean getPurchasable() {
        return purchasable;
    }

    public void setPurchasable(final Boolean purchasable) {
        this.purchasable = purchasable;
    }

    public List<UUID> getHeadquarterIds() {
        return headquarterIds;
    }

    public void setHeadquarterIds(final List<UUID> headquarterIds) {
        this.headquarterIds = headquarterIds;
    }
}
