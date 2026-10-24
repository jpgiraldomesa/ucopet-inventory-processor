package com.uco.ucopetinventory.repository.catalog;

import com.uco.ucopetinventory.domain.catalog.CatalogItemDomain;
import com.uco.ucopetinventory.domain.catalog.enums.CatalogItemType;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ICatalogItemRepository extends JpaRepository<CatalogItemDomain, UUID> {

    Optional<CatalogItemDomain> findByIdAndType(UUID id, CatalogItemType type);
}
