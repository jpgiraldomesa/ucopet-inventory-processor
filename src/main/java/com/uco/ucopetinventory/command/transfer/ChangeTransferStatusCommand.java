package com.uco.ucopetinventory.command.transfer;

import com.uco.ucopetinventory.domain.transfer.TransferStatus;
import java.util.UUID;

public record ChangeTransferStatusCommand(UUID id, TransferStatus status, UUID performedBy) {
}