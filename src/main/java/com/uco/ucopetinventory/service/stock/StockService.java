package com.uco.ucopetinventory.service.stock;

import com.uco.ucopetinventory.domain.stock.StockDomain;
import com.uco.ucopetinventory.repository.stock.IStockRepository;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final IStockRepository stockRepository;

    public StockService(IStockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Transactional
    public void adjust(StockDomain message) {
        if (message.getProductId() == null || message.getHeadquarterId() == null) {
            throw new IllegalArgumentException("El ajuste de stock necesita producto y sede");
        }
        if (message.getQuantity() == null || message.getQuantity() == 0) {
            throw new IllegalArgumentException("La cantidad a ajustar no puede ser nula ni cero");
        }
        if (stockRepository.addQuantity(message.getProductId(), message.getHeadquarterId(), message.getQuantity()) == 0) {
            createOrReject(message);
        }
    }

    private void createOrReject(StockDomain message) {
        if (message.getQuantity() > 0) {
            stockRepository.createOrAddQuantity(
                    UUID.randomUUID(), message.getProductId(), message.getHeadquarterId(), message.getQuantity());
            return;
        }
        throw new IllegalArgumentException(
                "La operacion dejaria el stock en negativo, o no existe stock de ese producto en esa sede");
    }
}
