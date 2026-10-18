package com.uco.ucopetinventory.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:inventory.properties")
@EnableConfigurationProperties(InventoryQueueProperties.class)
public class RabbitTopology {

    private final InventoryQueueProperties properties;

    public RabbitTopology(InventoryQueueProperties properties) {
        this.properties = properties;
    }

    @Bean
    Declarables inventoryWorkQueues() {
        List<Declarable> declarables = new ArrayList<>();
        for (InventoryQueueProperties.Queue names : properties.workQueues()) {
            DirectExchange exchange = new DirectExchange(names.exchangeName(), true, false);
            Queue queue = QueueBuilder.durable(names.queueName())
                    .deadLetterExchange(properties.deadLetter().exchangeName())
                    .build();
            declarables.add(exchange);
            declarables.add(queue);
            declarables.add(BindingBuilder.bind(queue).to(exchange).with(names.routingKeyName()));
        }
        return new Declarables(declarables);
    }

    @Bean
    Declarables inventoryDeadLetter() {
        TopicExchange exchange = new TopicExchange(properties.deadLetter().exchangeName(), true, false);
        Queue queue = QueueBuilder.durable(properties.deadLetter().queueName()).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with("#");
        return new Declarables(exchange, queue, binding);
    }
}
