package com.uco.ucopetinventory.service.stock;

import com.uco.ucopetinventory.dto.stock.StockCommand;
import com.uco.ucopetinventory.repository.stock.IStockRepository;
import com.uco.ucopetinventory.util.UnprocessableCommandException;

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
    public void adjust(StockCommand command) {
        if (command.productId() == null || command.headquarterId() == null) {
            throw new UnprocessableCommandException("El ajuste de stock necesita producto y sede");
        }
        if (command.quantity() == null || command.quantity() == 0) {
            throw new UnprocessableCommandException("La cantidad a ajustar no puede ser nula ni cero");
        }
        if (stockRepository.addQuantity(command.productId(), command.headquarterId(), command.quantity()) == 0) {
            createOrReject(command);
        }
    }

    private void createOrReject(StockCommand command) {
        if (command.quantity() > 0) {
            stockRepository.createOrAddQuantity(
                    UUID.randomUUID(), command.productId(), command.headquarterId(), command.quantity());
            return;
        }
        throw new UnprocessableCommandException(
                "La operacion dejaria el stock en negativo, o no existe stock de ese producto en esa sede");
    }
}
