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
public class ServiceDeactivationReceiverMessage {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceDeactivationReceiverMessage.class);

    private final CatalogService catalogService;
    private final MapperJsonObject mapperJsonObject;

    public ServiceDeactivationReceiverMessage(CatalogService catalogService, MapperJsonObject mapperJsonObject) {
        this.catalogService = catalogService;
        this.mapperJsonObject = mapperJsonObject;
    }

    @RabbitListener(queues = "${inventory.service.deactivate.queue-name}")
    public void receiveMessage(Message message) {
        Object idMessage = message.getMessageProperties().getHeader("idMensaje");
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        LOG.info("[SERVICE-DEACTIVATE] recibido idMensaje={}", idMessage);
        CatalogItemDomain item = mapperJsonObject.execute(json, CatalogItemDomain.class)
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        "El mensaje no es el JSON de un servicio: " + json));
        try {
            catalogService.deactivate(CatalogItemType.SERVICE, item.getId());
        } catch (IllegalArgumentException e) {
            LOG.warn("[SERVICE-DEACTIVATE] rechazado idMensaje={}: {}", idMessage, e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
