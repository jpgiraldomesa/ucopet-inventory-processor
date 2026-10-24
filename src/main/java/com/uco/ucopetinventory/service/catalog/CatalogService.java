package com.uco.ucopetinventory.service.catalog;

import com.uco.ucopetinventory.domain.catalog.CatalogItemDomain;
import com.uco.ucopetinventory.domain.catalog.CatalogItemHeadquarterDomain;
import com.uco.ucopetinventory.domain.catalog.enums.CatalogItemType;
import com.uco.ucopetinventory.repository.catalog.ICatalogItemHeadquarterRepository;
import com.uco.ucopetinventory.repository.catalog.ICatalogItemRepository;
import java.util.List;
import java.util.Optional;
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
    public void save(CatalogItemType type, CatalogItemDomain message) {
        if (message == null || message.getId() == null) {
            throw new IllegalArgumentException("El mensaje no trae el id: lo asigna la API antes de publicar");
        }
        Optional<CatalogItemDomain> existing = itemRepository.findById(message.getId());
        if (existing.isPresent()) {
            if (existing.get().getType() != type) {
                throw new IllegalArgumentException("El id " + message.getId() + " ya pertenece a otro tipo de item");
            }
            update(existing.get(), type, message);
        } else {
            create(type, message);
        }
    }

    @Transactional
    public void deactivate(CatalogItemType type, UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("El mensaje no trae el id del item a dar de baja");
        }
        CatalogItemDomain item = itemRepository.findByIdAndType(id, type)
                .orElseThrow(() -> new IllegalArgumentException("No existe un " + type + " con id " + id));
        item.setActive(false);
        itemRepository.save(item);
    }

    private void create(CatalogItemType type, CatalogItemDomain message) {
        CatalogItemDomain item = new CatalogItemDomain(message.getId(), type);
        item.setName(message.getName());
        item.setDescription(message.getDescription());
        item.setImageUrl(message.getImageUrl());
        item.setTaxCategory(message.getTaxCategory());
        item.setSellable(message.getSellable());
        item.setCategory(message.getCategory());
        item.setActive(true);
        item.setPrice(Boolean.TRUE.equals(message.getSellable()) ? message.getPrice() : null);
        if (type == CatalogItemType.SERVICE) {
            item.setPurchasable(message.getPurchasable());
        }
        itemRepository.save(item);
        if (type == CatalogItemType.SERVICE) {
            replaceHeadquarters(message.getId(), message.getHeadquarterIds());
        }
    }

    private void update(CatalogItemDomain item, CatalogItemType type, CatalogItemDomain message) {
        if (message.getName() != null) {
            item.setName(message.getName());
        }
        if (message.getDescription() != null) {
            item.setDescription(message.getDescription());
        }
        if (message.getImageUrl() != null) {
            item.setImageUrl(message.getImageUrl());
        }
        if (message.getPrice() != null) {
            item.setPrice(message.getPrice());
        }
        if (message.getTaxCategory() != null) {
            item.setTaxCategory(message.getTaxCategory());
        }
        if (message.getSellable() != null) {
            item.setSellable(message.getSellable());
        }
        if (message.getCategory() != null) {
            item.setCategory(message.getCategory());
        }
        if (type == CatalogItemType.SERVICE && message.getPurchasable() != null) {
            item.setPurchasable(message.getPurchasable());
        }
        if (!Boolean.TRUE.equals(item.getSellable())) {
            item.setPrice(null);
        }
        itemRepository.save(item);
        if (type == CatalogItemType.SERVICE && message.getHeadquarterIds() != null) {
            replaceHeadquarters(item.getId(), message.getHeadquarterIds());
        }
    }

    private void replaceHeadquarters(UUID itemId, List<UUID> headquarterIds) {
        headquarterRepository.deleteByItemId(itemId);
        if (headquarterIds == null) {
            return;
        }
        for (UUID headquarterId : headquarterIds) {
            headquarterRepository.save(new CatalogItemHeadquarterDomain(
                    UUID.randomUUID(), itemId, headquarterId));
        }
    }
}
