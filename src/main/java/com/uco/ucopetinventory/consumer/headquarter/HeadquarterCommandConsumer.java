package com.uco.ucopetinventory.consumer.headquarter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.uco.ucopetinventory.command.headquarter.DeactivateHeadquarterCommand;
import com.uco.ucopetinventory.command.headquarter.SaveHeadquarterCommand;
import com.uco.ucopetinventory.exception.headquarter.BusinessException;
import com.uco.ucopetinventory.service.headquarter.HeadquarterProcessorService;
import com.uco.ucopetinventory.util.gson.MapperJsonObject;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class HeadquarterCommandConsumer {

    private final HeadquarterProcessorService service;
    private final MapperJsonObject mapperJsonObject;

    public HeadquarterCommandConsumer(HeadquarterProcessorService service,
                                      MapperJsonObject mapperJsonObject) {
        this.service = service;
        this.mapperJsonObject = mapperJsonObject;
    }

    @RabbitListener(queues = "${inventory.headquarter.save.queue-name}")
    @Transactional
    public void onSave(Message message) {
        service.proccessSave(leer(message, SaveHeadquarterCommand.class));
    }

    @RabbitListener(queues = "${inventory.headquarter.deactivate.queue-name}")
    @Transactional
    public void onDeactivate(Message message) {
        service.proccessDeactivate(leer(message, DeactivateHeadquarterCommand.class));
    }

    private <T> T leer(Message message, Class<T> tipo) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        return mapperJsonObject.execute(body, tipo)
                .orElseThrow(() -> new BusinessException("Mensaje vacio o ilegible"));
    }
}