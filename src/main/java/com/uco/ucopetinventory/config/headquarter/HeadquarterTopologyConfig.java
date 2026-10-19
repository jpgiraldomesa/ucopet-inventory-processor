package com.uco.ucopetinventory.config.headquarter;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;

public class HeadquarterTopologyConfig {

    public static final String DLX = "ucopet.inventory.headquarter.dlx";
    public static final String SAVE_DLQ = "ucopet.inventory.headquarter.save.dlq";
    public static final String DEACTIVATE_DLQ = "ucopet.inventory.headquarter.deactivate.dlq";

    private final HeadquarterQueueConfig queueConfig;

    public HeadquarterTopologyConfig(HeadquarterQueueConfig queueConfig) {
        this.queueConfig = queueConfig;
    }

    // ---------- save ----------

    @Bean
    public DirectExchange saveExchange() {
        return ExchangeBuilder.directExchange(queueConfig.getSave().getExchangeName())
                .durable(true).build();
    }

    @Bean
    public Queue saveQueue() {
        return QueueBuilder.durable(queueConfig.getSave().getQueueName())
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(SAVE_DLQ)
                .build();
    }

    @Bean
    public Binding saveBinding() {
        return BindingBuilder.bind(saveQueue())
                .to(saveExchange())
                .with(queueConfig.getSave().getRoutingKeyName());
    }

    // ---------- deactivate ----------

    @Bean
    public DirectExchange deactivateExchange() {
        return ExchangeBuilder.directExchange(queueConfig.getDeactivate().getExchangeName())
                .durable(true).build();
    }

    @Bean
    public Queue deactivateQueue() {
        return QueueBuilder.durable(queueConfig.getDeactivate().getQueueName())
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DEACTIVATE_DLQ)
                .build();
    }

    @Bean
    public Binding deactivateBinding() {
        return BindingBuilder.bind(deactivateQueue())
                .to(deactivateExchange())
                .with(queueConfig.getDeactivate().getRoutingKeyName());
    }

    // ---------- Dead letter ----------

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue saveDeadLetterQueue() {
        return QueueBuilder.durable(SAVE_DLQ).build();
    }

    @Bean
    public Binding saveDeadLetterBinding() {
        return BindingBuilder.bind(saveDeadLetterQueue()).to(deadLetterExchange()).with(SAVE_DLQ);
    }

    @Bean
    public Queue deactivateDeadLetterQueue() {
        return QueueBuilder.durable(DEACTIVATE_DLQ).build();
    }

    @Bean
    public Binding deactivateDeadLetterBinding() {
        return BindingBuilder.bind(deactivateDeadLetterQueue())
                .to(deadLetterExchange()).with(DEACTIVATE_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
