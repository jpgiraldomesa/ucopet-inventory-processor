package com.uco.ucopetinventory.messaging.catalog;

import com.uco.ucopetinventory.domain.catalog.CatalogItemDomain;
import com.uco.ucopetinventory.domain.catalog.enums.CatalogItemType;
import com.uco.ucopetinventory.service.catalog.CatalogService;
import com.uco.ucopetinventory.util.gson.MapperJsonObject;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ProductSaveReceiverMessage {

    private static final Logger LOG = LoggerFactory.getLogger(ProductSaveReceiverMessage.class);

    private final CatalogService catalogService;
    private final MapperJsonObject mapperJsonObject;

    public ProductSaveReceiverMessage(CatalogService catalogService, MapperJsonObject mapperJsonObject) {
        this.catalogService = catalogService;
        this.mapperJsonObject = mapperJsonObject;
    }

    @RabbitListener(queues = "${inventory.product.save.queue-name}")
    public void receiveMessage(Message message) {
        Object idMessage = message.getMessageProperties().getHeader("idMensaje");
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        LOG.info("[PRODUCT-SAVE] recibido idMensaje={}", idMessage);
        CatalogItemDomain item = mapperJsonObject.execute(json, CatalogItemDomain.class)
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        "El mensaje no es el JSON de un producto: " + json));
        try {
            catalogService.save(CatalogItemType.PRODUCT, item);
        } catch (IllegalArgumentException e) {
            LOG.warn("[PRODUCT-SAVE] rechazado idMensaje={}: {}", idMessage, e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
