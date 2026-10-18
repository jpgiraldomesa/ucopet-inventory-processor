package com.uco.ucopetinventory.dto.stock;

import java.util.UUID;

public record StockCommand(UUID productId, UUID headquarterId, Integer quantity) {
}
