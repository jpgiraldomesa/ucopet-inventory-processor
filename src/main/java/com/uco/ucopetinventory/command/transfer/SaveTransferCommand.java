package com.uco.ucopetinventory.command.transfer;

import com.uco.ucopetinventory.domain.transfer.TransferStatus;
import java.util.UUID;

public record SaveTransferCommand(UUID id, UUID originHeadquarterId, UUID destinationHeadquarterId,
                                  UUID productId, Integer quantity, TransferStatus status,
                                  String observations, UUID performedBy) {
}