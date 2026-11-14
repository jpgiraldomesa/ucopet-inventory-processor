package com.uco.ucopetinventory.consumer.headquarter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rabbitmq.client.Channel;
import com.uco.ucopetinventory.command.headquarter.DeactivateHeadquarterCommand;
import com.uco.ucopetinventory.command.headquarter.SaveHeadquarterCommand;
import com.uco.ucopetinventory.config.InstantTypeAdapter;
import com.uco.ucopetinventory.exception.BusinessException;
import com.uco.ucopetinventory.service.HeadquarterProcessorService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class HeadquarterCommandConsumer {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter().nullSafe())
            .create();

    private final HeadquarterProcessorService service;

    public HeadquarterCommandConsumer(HeadquarterProcessorService service) {
        this.service = service;
    }

    @RabbitListener(queues = "${inventory.headquarter.save.queue-name}")
    public void onSave(String payload,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        procesar(channel, deliveryTag,
                () -> service.procesarSave(leer(payload, SaveHeadquarterCommand.class)));
    }

    @RabbitListener(queues = "${inventory.headquarter.deactivate.queue-name}")
    public void onDeactivate(String payload,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        procesar(channel, deliveryTag,
                () -> service.procesarDeactivate(leer(payload, DeactivateHeadquarterCommand.class)));
    }

    private <T> T leer(String payload, Class<T> tipo) {
        T command = GSON.fromJson(payload, tipo);
        if (command == null) {
            throw new BusinessException("Mensaje vacío o ilegible");
        }
        return command;
    }

    private void procesar(Channel channel, long deliveryTag, Runnable accion) throws IOException {
        try {
            accion.run();
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            channel.basicNack(deliveryTag, false, false);
        }
    }
}