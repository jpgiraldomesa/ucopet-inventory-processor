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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ProductSaveReceiverMessage {

    private static final Logger LOG = LoggerFactory.getLogger(ProductSaveReceiverMessage.class);

    private final ProductService productService;
    private final MapperJsonObject mapperJsonObject;
    private final TransactionTemplate transactionTemplate;

    public ProductSaveReceiverMessage(ProductService productService, MapperJsonObject mapperJsonObject,
            TransactionTemplate transactionTemplate) {
        this.productService = productService;
        this.mapperJsonObject = mapperJsonObject;
        this.transactionTemplate = transactionTemplate;
    }

    @RabbitListener(queues = "${inventory.product.save.queue-name}", concurrency = "1")
    public Message receiveMessage(Message message) {
        Object idMessage = message.getMessageProperties().getHeader("idMensaje");
        boolean waitsForReply = message.getMessageProperties().getReplyTo() != null;
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        LOG.info("[PRODUCT-SAVE] recibido idMensaje={}", idMessage);
        ProductDomain item = mapperJsonObject.execute(json, ProductDomain.class)
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        "El mensaje no es el JSON de un producto: " + json));
        try {
            transactionTemplate.executeWithoutResult(status -> {
                productService.save(item);
                ReceiverMessageReply.requireOnTime(message);
            });
            return waitsForReply ? ReceiverMessageReply.applied() : null;
        } catch (IllegalArgumentException e) {
            LOG.warn("[PRODUCT-SAVE] rechazado idMensaje={}: {}", idMessage, e.getMessage());
            if (waitsForReply) {
                return ReceiverMessageReply.rejected(e.getMessage());
            }
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        } catch (DataIntegrityViolationException e) {
            boolean duplicate = ReceiverMessageReply.isDuplicateName(e, "ux_product_name");
            String reason = duplicate ? "Ya existe un producto con este nombre"
                    : "No se pudo guardar el producto: datos inválidos";
            LOG.warn("[PRODUCT-SAVE] rechazado idMensaje={}: {}", idMessage, reason);
            if (waitsForReply) {
                return duplicate ? ReceiverMessageReply.duplicateName(reason) : ReceiverMessageReply.rejected(reason);
            }
            throw new AmqpRejectAndDontRequeueException(reason, e);
        }
    }
}
