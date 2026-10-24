package com.uco.ucopetinventory.dto.catalog;

import com.uco.ucopetinventory.domain.catalog.enums.CatalogCategory;
import com.uco.ucopetinventory.domain.catalog.enums.TaxCategory;

import java.util.List;
import java.util.UUID;


public record CatalogItemCommand(
        UUID id,
        String name,
        String description,
        String imageUrl,
        Integer price,
        TaxCategory taxCategory,
        Boolean sellable,
        CatalogCategory category,
        Boolean purchasable,
        List<UUID> headquarterIds) {
}
