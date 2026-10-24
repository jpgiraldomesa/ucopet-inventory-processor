package com.uco.ucopetinventory.messaging;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@TestConfiguration
public class InventoryTestQueues {

    static final List<String> KEYS = List.of(
            "inventory.product.save",
            "inventory.product.deactivate",
            "inventory.service.save",
            "inventory.service.deactivate",
            "inventory.stock.adjust");

    @Bean
    public Declarables inventoryQueues(Environment environment) {
        List<Declarable> declarables = new ArrayList<>();
        for (String key : KEYS) {
            DirectExchange exchange = new DirectExchange(environment.getRequiredProperty(key + ".exchange-name"), true, false);
            Queue queue = QueueBuilder.durable(environment.getRequiredProperty(key + ".queue-name")).build();
            declarables.add(exchange);
            declarables.add(queue);
            declarables.add(BindingBuilder.bind(queue).to(exchange)
                    .with(environment.getRequiredProperty(key + ".routing-key-name")));
        }
        return new Declarables(declarables);
    }
}
