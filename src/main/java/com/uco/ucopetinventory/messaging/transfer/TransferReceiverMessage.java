package com.uco.ucopetinventory.messaging.transfer;

import com.uco.ucopetinventory.command.transfer.CancelTransferCommand;
import com.uco.ucopetinventory.command.transfer.ChangeTransferStatusCommand;
import com.uco.ucopetinventory.command.transfer.SaveTransferCommand;
import com.uco.ucopetinventory.service.transfer.TransferProcessorService;
import com.uco.ucopetinventory.util.gson.MapperJsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Component
public class TransferReceiverMessage {

    private static final Logger LOG = LoggerFactory.getLogger(TransferReceiverMessage.class);

    private final TransferProcessorService transferService;
    private final MapperJsonObject mapperJsonObject;
    private final TransactionTemplate transactionTemplate;

    public TransferReceiverMessage(TransferProcessorService transferService, MapperJsonObject mapperJsonObject,
                                   TransactionTemplate transactionTemplate) {
        this.transferService = transferService;
        this.mapperJsonObject = mapperJsonObject;
        this.transactionTemplate = transactionTemplate;
    }

    @RabbitListener(queues = "${inventory.transfer.save.queue-name}", concurrency = "1")
    public void onSave(Message message) {
        process(message, "TRANSFER-SAVE", SaveTransferCommand.class, transferService::save);
    }

    @RabbitListener(queues = "${inventory.transfer.status.queue-name}", concurrency = "1")
    public void onStatus(Message message) {
        process(message, "TRANSFER-STATUS", ChangeTransferStatusCommand.class, transferService::changeStatus);
    }

    @RabbitListener(queues = "${inventory.transfer.cancel.queue-name}", concurrency = "1")
    public void onCancel(Message message) {
        process(message, "TRANSFER-CANCEL", CancelTransferCommand.class, transferService::cancel);
    }

    private <T> void process(Message message, String tag, Class<T> type, Consumer<T> action) {
        Object idMessage = message.getMessageProperties().getHeader("idMensaje");
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        LOG.info("[{}] recibido idMensaje={}", tag, idMessage);
        T command = mapperJsonObject.execute(json, type)
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        "El mensaje no es el JSON esperado (" + type.getSimpleName() + "): " + json));
        try {
            transactionTemplate.executeWithoutResult(status -> action.accept(command));
        } catch (IllegalArgumentException e) {
            LOG.warn("[{}] rechazado idMensaje={}: {}", tag, idMessage, e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}