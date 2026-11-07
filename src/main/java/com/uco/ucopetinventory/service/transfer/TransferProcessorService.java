package com.uco.ucopetinventory.service.transfer;

import com.uco.ucopetinventory.command.transfer.CancelTransferCommand;
import com.uco.ucopetinventory.command.transfer.ChangeTransferStatusCommand;
import com.uco.ucopetinventory.command.transfer.SaveTransferCommand;
import com.uco.ucopetinventory.domain.product.StockMovementDomain;
import com.uco.ucopetinventory.domain.product.enums.StockMovementReason;
import com.uco.ucopetinventory.domain.transfer.TransferDomain;
import com.uco.ucopetinventory.domain.transfer.TransferStatus;
import com.uco.ucopetinventory.repository.headquarter.HeadquarterRepository;
import com.uco.ucopetinventory.repository.transfer.ITransferRepository;
import com.uco.ucopetinventory.service.product.StockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class TransferProcessorService {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Bogota");

    private final ITransferRepository transferRepository;
    private final HeadquarterRepository headquarterRepository;
    private final StockService stockService;

    public TransferProcessorService(ITransferRepository transferRepository,
                                    HeadquarterRepository headquarterRepository,
                                    StockService stockService) {
        this.transferRepository = transferRepository;
        this.headquarterRepository = headquarterRepository;
        this.stockService = stockService;
    }

    @Transactional
    public void save(SaveTransferCommand command) {
        validateSave(command);
        TransferDomain transfer = transferRepository.findLockedById(command.id()).orElse(null);
        if (transfer == null) {
            transfer = new TransferDomain();
            transfer.setId(command.id());
            transfer.setStatus(TransferStatus.PENDING);
            transfer.setCreatedBy(command.performedBy());
            transfer.setCreatedAt(now());
        } else {
            requireStatus(transfer, TransferStatus.PENDING, "editar");
            transfer.setUpdatedBy(command.performedBy());
            transfer.setUpdatedAt(now());
        }
        transfer.setOriginHeadquarterId(command.originHeadquarterId());
        transfer.setDestinationHeadquarterId(command.destinationHeadquarterId());
        transfer.setProductId(command.productId());
        transfer.setQuantity(command.quantity());
        transfer.setObservations(command.observations());
        transferRepository.save(transfer);
    }

    @Transactional
    public void changeStatus(ChangeTransferStatusCommand command) {
        TransferDomain transfer = findLocked(command.id());

        if (transfer.getStatus() == TransferStatus.PENDING && command.status() == TransferStatus.IN_PROGRESS) {
            requireActiveHeadquarter(transfer.getOriginHeadquarterId(), "origen");
            adjust(transfer, transfer.getOriginHeadquarterId(), -transfer.getQuantity(),
                    StockMovementReason.TRANSFER_OUT, "Salida por traslado " + transfer.getId(),
                    command.performedBy());
        } else if (transfer.getStatus() == TransferStatus.IN_PROGRESS
                && command.status() == TransferStatus.COMPLETED) {
            requireActiveHeadquarter(transfer.getDestinationHeadquarterId(), "destino");
            adjust(transfer, transfer.getDestinationHeadquarterId(), transfer.getQuantity(),
                    StockMovementReason.TRANSFER_IN, "Entrada por traslado " + transfer.getId(),
                    command.performedBy());
        } else {
            throw new IllegalArgumentException("Transicion invalida: de " + transfer.getStatus()
                    + " a " + command.status());
        }
        touch(transfer, command.status(), command.performedBy());
    }

    @Transactional
    public void cancel(CancelTransferCommand command) {
        TransferDomain transfer = findLocked(command.id());

        if (transfer.getStatus() == TransferStatus.IN_PROGRESS) {
            // La mercancia ya salio del origen: se devuelve
            requireActiveHeadquarter(transfer.getOriginHeadquarterId(), "origen");
            adjust(transfer, transfer.getOriginHeadquarterId(), transfer.getQuantity(),
                    StockMovementReason.TRANSFER_IN, "Reverso por cancelacion del traslado " + transfer.getId(),
                    command.performedBy());
        } else if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Solo se puede cancelar un traslado Pendiente o En Proceso. Estado actual: "
                            + transfer.getStatus());
        }
        touch(transfer, TransferStatus.CANCELLED, command.performedBy());
    }

    private void adjust(TransferDomain transfer, UUID headquarterId, int quantity,
                        StockMovementReason reason, String note, UUID performedBy) {
        stockService.adjust(new StockMovementDomain(transfer.getProductId(), headquarterId, quantity,
                reason, note, performedBy));
    }

    private void touch(TransferDomain transfer, TransferStatus status, UUID performedBy) {
        transfer.setStatus(status);
        transfer.setUpdatedBy(performedBy);
        transfer.setUpdatedAt(now());
        transferRepository.save(transfer);
    }

    private TransferDomain findLocked(UUID id) {
        return transferRepository.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe un traslado con id " + id));
    }

    private void requireStatus(TransferDomain transfer, TransferStatus required, String action) {
        if (transfer.getStatus() != required) {
            throw new IllegalArgumentException("Solo se puede " + action + " un traslado en estado "
                    + required + ". Estado actual: " + transfer.getStatus());
        }
    }

    private void requireActiveHeadquarter(UUID id, String role) {
        boolean active = headquarterRepository.findById(id)
                .map(h -> Boolean.TRUE.equals(h.getActive())).orElse(false);
        if (!active) {
            throw new IllegalArgumentException("La sede de " + role + " no existe o no esta activa");
        }
    }

    private void validateSave(SaveTransferCommand c) {
        if (c.id() == null || c.originHeadquarterId() == null || c.destinationHeadquarterId() == null
                || c.productId() == null || c.performedBy() == null) {
            throw new IllegalArgumentException("El mensaje del traslado esta incompleto");
        }
        if (c.originHeadquarterId().equals(c.destinationHeadquarterId())) {
            throw new IllegalArgumentException("La sede de origen y la de destino no pueden ser la misma");
        }
        if (c.quantity() == null || c.quantity() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZONE_ID);
    }
}