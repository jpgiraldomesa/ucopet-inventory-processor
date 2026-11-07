package com.uco.ucopetinventory.command.transfer;

import java.util.UUID;

public record CancelTransferCommand(UUID id, UUID performedBy) {
}