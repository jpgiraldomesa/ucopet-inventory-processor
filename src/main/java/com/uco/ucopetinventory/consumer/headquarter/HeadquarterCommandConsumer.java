package com.uco.ucopetinventory.consumer.headquarter;

import com.rabbitmq.client.Channel;
import com.uco.ucopetinventory.command.headquarter.DeactivateHeadquarterCommand;
import com.uco.ucopetinventory.command.headquarter.SaveHeadquarterCommand;
import com.uco.ucopetinventory.service.headquarter.HeadquarterProcessorService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class HeadquarterCommandConsumer {

    private final HeadquarterProcessorService service;

    public HeadquarterCommandConsumer(HeadquarterProcessorService service) {
        this.service = service;
    }

    @RabbitListener(queues = "${inventory.headquarter.save.queue-name}")
    public void onSave(@Payload SaveHeadquarterCommand command,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        proccess(channel,deliveryTag, () -> service.proccessSave(command));
    }

    @RabbitListener(queues = "${inventory.headquarter.deactivate.queue-name}")
    public void onDeactivate(@Payload DeactivateHeadquarterCommand command,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        proccess(channel,deliveryTag, () -> service.proccessDeactivate(command));
    }

    private void proccess(Channel channel, long deliveryTag, Runnable action) throws IOException {
        try {
            action.run();
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            channel.basicNack(deliveryTag, false, false);
        }
    }

}
