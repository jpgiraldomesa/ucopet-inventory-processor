package com.uco.ucopetinventory.messaging.product;

import com.uco.ucopetinventory.domain.product.ServiceDomain;
import com.uco.ucopetinventory.service.product.ServiceService;
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
public class ServiceSaveReceiverMessage {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceSaveReceiverMessage.class);

    private final ServiceService serviceService;
    private final MapperJsonObject mapperJsonObject;
    private final TransactionTemplate transactionTemplate;

    public ServiceSaveReceiverMessage(ServiceService serviceService, MapperJsonObject mapperJsonObject,
            TransactionTemplate transactionTemplate) {
        this.serviceService = serviceService;
        this.mapperJsonObject = mapperJsonObject;
        this.transactionTemplate = transactionTemplate;
    }

    @RabbitListener(queues = "${inventory.service.save.queue-name}", concurrency = "1")
    public Message receiveMessage(Message message) {
        Object idMessage = message.getMessageProperties().getHeader("idMensaje");
        boolean waitsForReply = message.getMessageProperties().getReplyTo() != null;
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        LOG.info("[SERVICE-SAVE] recibido idMensaje={}", idMessage);
        ServiceDomain item = mapperJsonObject.execute(json, ServiceDomain.class)
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        "El mensaje no es el JSON de un servicio: " + json));
        try {
            transactionTemplate.executeWithoutResult(status -> {
                serviceService.save(item);
                ReceiverMessageReply.requireOnTime(message);
            });
            return waitsForReply ? ReceiverMessageReply.applied() : null;
        } catch (IllegalArgumentException e) {
            LOG.warn("[SERVICE-SAVE] rechazado idMensaje={}: {}", idMessage, e.getMessage());
            if (waitsForReply) {
                return ReceiverMessageReply.rejected(e.getMessage());
            }
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        } catch (DataIntegrityViolationException e) {
            boolean duplicate = ReceiverMessageReply.isDuplicateName(e, "ux_service_name");
            String reason = duplicate ? "Ya existe un servicio con este nombre"
                    : "No se pudo guardar el servicio: datos inválidos";
            LOG.warn("[SERVICE-SAVE] rechazado idMensaje={}: {}", idMessage, reason);
            if (waitsForReply) {
                return duplicate ? ReceiverMessageReply.duplicateName(reason) : ReceiverMessageReply.rejected(reason);
            }
            throw new AmqpRejectAndDontRequeueException(reason, e);
        }
    }
}
