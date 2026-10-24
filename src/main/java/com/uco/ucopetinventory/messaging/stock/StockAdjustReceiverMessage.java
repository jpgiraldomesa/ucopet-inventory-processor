package com.uco.ucopetinventory.messaging.stock;

import com.uco.ucopetinventory.domain.stock.StockDomain;
import com.uco.ucopetinventory.service.stock.StockService;
import com.uco.ucopetinventory.util.gson.MapperJsonObject;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StockAdjustReceiverMessage {

    private static final Logger LOG = LoggerFactory.getLogger(StockAdjustReceiverMessage.class);

    private final StockService stockService;
    private final MapperJsonObject mapperJsonObject;

    public StockAdjustReceiverMessage(StockService stockService, MapperJsonObject mapperJsonObject) {
        this.stockService = stockService;
        this.mapperJsonObject = mapperJsonObject;
    }

    @RabbitListener(queues = "${inventory.stock.adjust.queue-name}")
    public void receiveMessage(Message message) {
        Object idMessage = message.getMessageProperties().getHeader("idMensaje");
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        LOG.info("[STOCK-ADJUST] recibido idMensaje={}", idMessage);
        StockDomain item = mapperJsonObject.execute(json, StockDomain.class)
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        "El mensaje no es el JSON de un ajuste de stock: " + json));
        try {
            stockService.adjust(item);
        } catch (IllegalArgumentException e) {
            LOG.warn("[STOCK-ADJUST] rechazado idMensaje={}: {}", idMessage, e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
