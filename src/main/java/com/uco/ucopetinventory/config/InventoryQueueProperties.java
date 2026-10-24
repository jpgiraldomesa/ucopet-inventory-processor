package com.uco.ucopetinventory.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inventory")
public record InventoryQueueProperties(
        ModuleQueues product,
        ModuleQueues service,
        StockQueues stock,
        DeadLetter deadLetter) {

    public record ModuleQueues(Queue save, Queue deactivate) {
    }

    public record StockQueues(Queue adjust) {
    }

    public record Queue(String exchangeName, String queueName, String routingKeyName) {
    }

    public record DeadLetter(String exchangeName, String queueName) {
    }

    public List<Queue> workQueues() {
        return List.of(product.save(), product.deactivate(), service.save(), service.deactivate(), stock.adjust());
    }
}
