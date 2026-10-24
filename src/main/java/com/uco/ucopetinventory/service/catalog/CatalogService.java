package com.uco.ucopetinventory.service.catalog;

import com.uco.ucopetinventory.domain.catalog.CatalogItemDomain;
import com.uco.ucopetinventory.domain.catalog.CatalogItemHeadquarterDomain;
import com.uco.ucopetinventory.domain.catalog.enums.CatalogItemType;
import com.uco.ucopetinventory.dto.catalog.CatalogItemCommand;
import com.uco.ucopetinventory.repository.catalog.ICatalogItemHeadquarterRepository;
import com.uco.ucopetinventory.repository.catalog.ICatalogItemRepository;
import com.uco.ucopetinventory.util.UnprocessableCommandException;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final ICatalogItemRepository itemRepository;
    private final ICatalogItemHeadquarterRepository headquarterRepository;

    public CatalogService(ICatalogItemRepository itemRepository,
                         ICatalogItemHeadquarterRepository headquarterRepository) {
        this.itemRepository = itemRepository;
        this.headquarterRepository = headquarterRepository;
    }

    @Transactional
    public void create(CatalogItemType type, CatalogItemCommand command) {
        requireId(command.id());
        CatalogItemDomain item = new CatalogItemDomain(command.id(), type);
        item.setName(command.name());
        item.setDescription(command.description());
        item.setImageUrl(command.imageUrl());
        item.setTaxCategory(command.taxCategory());
        item.setSellable(command.sellable());
        item.setCategory(command.category());
        item.setActive(true);
        item.setPrice(Boolean.TRUE.equals(command.sellable()) ? command.price() : null);
        if (type == CatalogItemType.SERVICE) {
            item.setPurchasable(command.purchasable());
        }
        itemRepository.save(item);
        if (type == CatalogItemType.SERVICE) {
            replaceHeadquarters(command.id(), command.headquarterIds());
        }
    }

    @Transactional
    public void update(CatalogItemType type, CatalogItemCommand command) {
        requireId(command.id());
        CatalogItemDomain item = findOrThrow(type, command.id());
        if (command.name() != null) {
            item.setName(command.name());
        }
        if (command.description() != null) {
            item.setDescription(command.description());
        }
        if (command.imageUrl() != null) {
            item.setImageUrl(command.imageUrl());
        }
        if (command.price() != null) {
            item.setPrice(command.price());
        }
        if (command.taxCategory() != null) {
            item.setTaxCategory(command.taxCategory());
        }
        if (command.sellable() != null) {
            item.setSellable(command.sellable());
        }
        if (command.category() != null) {
            item.setCategory(command.category());
        }
        if (type == CatalogItemType.SERVICE && command.purchasable() != null) {
            item.setPurchasable(command.purchasable());
        }
        if (!Boolean.TRUE.equals(item.getSellable())) {
            item.setPrice(null);
        }
        itemRepository.save(item);
        if (type == CatalogItemType.SERVICE && command.headquarterIds() != null) {
            replaceHeadquarters(item.getId(), command.headquarterIds());
        }
    }

    @Transactional
    public void deactivate(CatalogItemType type, CatalogItemCommand command) {
        requireId(command.id());
        CatalogItemDomain item = findOrThrow(type, command.id());
        item.setActive(false);
        itemRepository.save(item);
    }

    private void replaceHeadquarters(UUID itemId, java.util.List<UUID> headquarterIds) {
        headquarterRepository.deleteByItemId(itemId);
        if (headquarterIds == null) {
            return;
        }
        for (UUID headquarterId : headquarterIds) {
            headquarterRepository.save(new CatalogItemHeadquarterDomain(
                    UUID.randomUUID(), itemId, headquarterId));
        }
    }

    private CatalogItemDomain findOrThrow(CatalogItemType type, UUID id) {
        return itemRepository.findByIdAndType(id, type)
                .orElseThrow(() -> new UnprocessableCommandException(
                        "No existe un " + type + " con id " + id));
    }

    private static void requireId(UUID id) {
        if (id == null) {
            throw new UnprocessableCommandException("El comando llego sin id: lo asigna la API antes de publicar");
        }
    }
}
