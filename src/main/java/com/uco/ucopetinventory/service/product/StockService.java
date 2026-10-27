package com.uco.ucopetinventory.service.product;

import com.uco.ucopetinventory.domain.product.StockDomain;
import com.uco.ucopetinventory.domain.product.StockMovementDomain;
import com.uco.ucopetinventory.domain.product.enums.StockMovementReason;
import com.uco.ucopetinventory.repository.product.IProductRepository;
import com.uco.ucopetinventory.repository.product.IStockMovementRepository;
import com.uco.ucopetinventory.repository.product.IStockRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final IStockRepository stockRepository;
    private final IProductRepository productRepository;
    private final IStockMovementRepository stockMovementRepository;

    public StockService(IStockRepository stockRepository, IProductRepository productRepository,
                        IStockMovementRepository stockMovementRepository) {
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional
    public StockDomain adjust(StockMovementDomain movement) {
        if (movement.getProductId() == null || movement.getHeadquarterId() == null) {
            throw new IllegalArgumentException("El ajuste de stock necesita producto y sede");
        }
        if (movement.getQuantity() == null || movement.getQuantity() == 0) {
            throw new IllegalArgumentException("La cantidad a ajustar no puede ser nula ni cero");
        }
        if (movement.getReason() == null) {
            movement.setReason(StockMovementReason.ADJUSTMENT);
        }
        Boolean active = productRepository.findLockedById(movement.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("No existe un producto con id "
                        + movement.getProductId()))
                .getActive();
        if (!Boolean.TRUE.equals(active)) {
            throw new IllegalArgumentException("No se puede ajustar el stock de un producto dado de baja");
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        StockDomain stock = stockRepository
                .findByProductIdAndHeadquarterId(movement.getProductId(), movement.getHeadquarterId())
                .orElse(null);
        if (stock == null) {
            if (movement.getQuantity() < 0) {
                throw new IllegalArgumentException("No existe stock de ese producto en esa sede para descontar");
            }
            stock = new StockDomain(UUID.randomUUID(), movement.getProductId(), movement.getHeadquarterId(),
                    movement.getQuantity());
        } else {
            int result = stock.getQuantity() + movement.getQuantity();
            if (result < 0) {
                throw new IllegalArgumentException("La operación dejaría el stock en negativo");
            }
            recordOpeningBalance(stock, now.minus(1, ChronoUnit.MICROS));
            stock.setQuantity(result);
        }
        StockDomain saved = stockRepository.save(stock);
        movement.setId(UUID.randomUUID());
        movement.setBalanceAfter(saved.getQuantity());
        movement.setOccurredAt(now);
        stockMovementRepository.save(movement);
        return saved;
    }

    private void recordOpeningBalance(StockDomain stock, Instant before) {
        if (stock.getQuantity() == 0
                || stockMovementRepository.existsByProductIdAndHeadquarterId(stock.getProductId(),
                        stock.getHeadquarterId())) {
            return;
        }
        StockMovementDomain opening = new StockMovementDomain(stock.getProductId(), stock.getHeadquarterId(),
                stock.getQuantity(), StockMovementReason.OPENING, "Saldo que había antes del historial", null);
        opening.setId(UUID.randomUUID());
        opening.setBalanceAfter(stock.getQuantity());
        opening.setOccurredAt(before);
        stockMovementRepository.save(opening);
    }
}
