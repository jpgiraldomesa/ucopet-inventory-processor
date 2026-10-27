package com.uco.ucopetinventory.messaging.product;

import com.uco.ucopetinventory.domain.product.StockDomain;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.dao.DataIntegrityViolationException;

final class ReceiverMessageReply {

    private static final String RESULT = "resultado";
    private static final String APPLIED = "aplicado";
    private static final String REJECTED = "rechazado";
    private static final String REASON = "motivo";
    private static final String DEADLINE = "plazo";

    private ReceiverMessageReply() {
    }

    static Message applied() {
        return MessageBuilder.withBody(new byte[0]).setHeader(RESULT, APPLIED).build();
    }

    static Message appliedStock(StockDomain saved) {
        return MessageBuilder.withBody(new byte[0])
                .setHeader(RESULT, APPLIED)
                .setHeader("stockId", saved.getId().toString())
                .setHeader("cantidad", saved.getQuantity())
                .build();
    }

    static Message rejected(String reason) {
        return MessageBuilder.withBody(new byte[0])
                .setHeader(RESULT, REJECTED)
                .setHeader(REASON, reason)
                .build();
    }

    static Message duplicateName(String reason) {
        return MessageBuilder.withBody(new byte[0])
                .setHeader(RESULT, REJECTED)
                .setHeader("tipo", "nombre-repetido")
                .setHeader(REASON, reason)
                .build();
    }

    static void requireOnTime(Message message) {
        Object deadline = message.getMessageProperties().getHeader(DEADLINE);
        if (deadline instanceof Number limit && System.currentTimeMillis() > limit.longValue()) {
            throw new IllegalArgumentException("La API dejó de esperar la respuesta: el cambio no se aplicó");
        }
    }

    static boolean isDuplicateName(DataIntegrityViolationException e, String uniqueIndex) {
        String cause = e.getMostSpecificCause().getMessage();
        return cause != null && cause.contains(uniqueIndex);
    }
}
