package com.uco.ucopetinventory.repository.catalog;

import com.uco.ucopetinventory.domain.catalog.CatalogItemHeadquarterDomain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ICatalogItemHeadquarterRepository extends JpaRepository<CatalogItemHeadquarterDomain, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CatalogItemHeadquarterDomain h WHERE h.itemId = :itemId")
    void deleteByItemId(@Param("itemId") UUID itemId);
}
