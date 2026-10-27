package com.uco.ucopetinventory.messaging.product;

import com.uco.ucopetinventory.domain.product.ProductDomain;
import com.uco.ucopetinventory.service.product.ProductService;
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
public class ProductDeactivationReceiverMessage {

    private static final Logger LOG = LoggerFactory.getLogger(ProductDeactivationReceiverMessage.class);

    private final ProductService productService;
    private final MapperJsonObject mapperJsonObject;
    private final TransactionTemplate transactionTemplate;

    public ProductDeactivationReceiverMessage(ProductService productService, MapperJsonObject mapperJsonObject,
            TransactionTemplate transactionTemplate) {
        this.productService = productService;
        this.mapperJsonObject = mapperJsonObject;
        this.transactionTemplate = transactionTemplate;
    }

    @RabbitListener(queues = "${inventory.product.deactivate.queue-name}", concurrency = "1")
    public Message receiveMessage(Message message) {
        Object idMessage = message.getMessageProperties().getHeader("idMensaje");
        boolean waitsForReply = message.getMessageProperties().getReplyTo() != null;
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        LOG.info("[PRODUCT-DEACTIVATE] recibido idMensaje={}", idMessage);
        ProductDomain item = mapperJsonObject.execute(json, ProductDomain.class)
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        "El mensaje no es el JSON de un producto: " + json));
        try {
            transactionTemplate.executeWithoutResult(status -> {
                productService.deactivate(item);
                ReceiverMessageReply.requireOnTime(message);
            });
            return waitsForReply ? ReceiverMessageReply.applied() : null;
        } catch (IllegalArgumentException e) {
            LOG.warn("[PRODUCT-DEACTIVATE] rechazado idMensaje={}: {}", idMessage, e.getMessage());
            if (waitsForReply) {
                return ReceiverMessageReply.rejected(e.getMessage());
            }
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
