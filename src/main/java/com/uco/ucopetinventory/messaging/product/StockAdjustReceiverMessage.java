package com.uco.ucopetinventory.messaging.product;

import com.uco.ucopetinventory.domain.product.StockDomain;
import com.uco.ucopetinventory.domain.product.StockMovementDomain;
import com.uco.ucopetinventory.service.product.StockService;
import com.uco.ucopetinventory.util.gson.MapperJsonObject;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class StockAdjustReceiverMessage {

    private static final Logger LOG = LoggerFactory.getLogger(StockAdjustReceiverMessage.class);

    private final StockService stockService;
    private final MapperJsonObject mapperJsonObject;
    private final TransactionTemplate transactionTemplate;

    public StockAdjustReceiverMessage(StockService stockService, MapperJsonObject mapperJsonObject,
            TransactionTemplate transactionTemplate) {
        this.stockService = stockService;
        this.mapperJsonObject = mapperJsonObject;
        this.transactionTemplate = transactionTemplate;
    }

    @RabbitListener(queues = "${inventory.stock.adjust.queue-name}", concurrency = "1")
    public Message receiveMessage(Message message) {
        Object idMessage = message.getMessageProperties().getHeader("idMensaje");
        boolean waitsForReply = message.getMessageProperties().getReplyTo() != null;
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        LOG.info("[STOCK-ADJUST] recibido idMensaje={}", idMessage);
        StockMovementDomain item = mapperJsonObject.execute(json, StockMovementDomain.class)
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        "El mensaje no es el JSON de un ajuste de stock: " + json));
        try {
            StockDomain saved = transactionTemplate.execute(status -> {
                StockDomain adjusted = stockService.adjust(item);
                ReceiverMessageReply.requireOnTime(message);
                return adjusted;
            });
            return waitsForReply ? ReceiverMessageReply.appliedStock(saved) : null;
        } catch (IllegalArgumentException e) {
            LOG.warn("[STOCK-ADJUST] rechazado idMensaje={}: {}", idMessage, e.getMessage());
            if (waitsForReply) {
                return ReceiverMessageReply.rejected(e.getMessage());
            }
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
