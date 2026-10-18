package com.uco.ucopetinventory.messaging.inventory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import com.uco.ucopetinventory.config.InventoryQueueProperties;
import com.uco.ucopetinventory.domain.catalog.CatalogItemDomain;
import com.uco.ucopetinventory.domain.catalog.enums.CatalogItemType;
import com.uco.ucopetinventory.repository.catalog.ICatalogItemRepository;
import com.uco.ucopetinventory.repository.messaging.IProcessedMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("El procesador de inventario, de punta a punta")
class ReceiverMessagesBrokerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private InventoryQueueProperties queues;

    @Autowired
    private ICatalogItemRepository itemRepository;

    @Autowired
    private IProcessedMessageRepository processedMessageRepository;

    private final UUID itemId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID deactivateEventId = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        itemRepository.deleteById(itemId);
        processedMessageRepository.deleteById(eventId);
        processedMessageRepository.deleteById(deactivateEventId);
    }

    @Test
    @DisplayName("un comando de crear producto termina siendo una fila, y el duplicado se descarta")
    void createProductCommand_endsUpInTheDatabase() {
        publish(envelope(eventId, "inventory.product.create", itemId, "Producto por el bus"),
                queues.product().save());

        CatalogItemDomain created = waitForItem(item -> true)
                .orElseThrow(() -> new AssertionError("el comando no llego a la base en " + TIMEOUT));

        assertThat(created.getName()).isEqualTo("Producto por el bus");
        assertThat(created.getType()).isEqualTo(CatalogItemType.PRODUCT);
        assertThat(created.getActive()).isTrue();
        assertThat(created.getPrice()).isEqualTo(7700);

        publish(envelope(eventId, "inventory.product.create", itemId, "Producto por el bus"),
                queues.product().save());
        pause();

        assertThat(itemRepository.findById(itemId)).isPresent();
        assertThat(processedMessageRepository.count()).isPositive();
    }

    @Test
    @DisplayName("un mensaje JSON como el que manda la API, con tildes, llega entero")
    void jsonMessageFromTheApi_keepsItsAccents() {
        String body = envelope(eventId, "inventory.product.create", itemId, "Arnés de ñandú");
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setHeader("idMessage", eventId.toString());
        rabbitTemplate.send(queues.product().save().exchangeName(), queues.product().save().routingKeyName(),
                MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).andProperties(properties).build());

        CatalogItemDomain created = waitForItem(item -> true)
                .orElseThrow(() -> new AssertionError("el mensaje JSON no llego a la base en " + TIMEOUT));

        assertThat(created.getName()).isEqualTo("Arnés de ñandú");
    }

    @Test
    @DisplayName("crear va por la cola save y dar de baja por la cola deactivate")
    void deactivate_travelsThroughItsOwnQueue() {
        publish(envelope(eventId, "inventory.product.create", itemId, "Producto para dar de baja"),
                queues.product().save());
        waitForItem(item -> true)
                .orElseThrow(() -> new AssertionError("el alta no llego a la base en " + TIMEOUT));

        publish(envelope(deactivateEventId, "inventory.product.deactivate", itemId, "Producto para dar de baja"),
                queues.product().deactivate());

        assertThat(waitForItem(item -> Boolean.FALSE.equals(item.getActive())))
                .as("la baja por %s tiene que dejar el producto inactivo", queues.product().deactivate().queueName())
                .isPresent();
    }

    @Test
    @DisplayName("un comando que falla va a la cola de descarte y no queda marcado como procesado")
    void failingCommand_goesToTheDeadLetterQueue() {
        UUID missingId = UUID.randomUUID();
        publish(envelope(eventId, "inventory.product.update", missingId, "No existe"),
                queues.product().save());

        assertThat(waitForDeadLetter())
                .as("el comando fallido tiene que aparecer en %s", queues.deadLetter().queueName())
                .isTrue();
        assertThat(processedMessageRepository.findById(eventId)).isEmpty();
        assertThat(itemRepository.findById(missingId)).isEmpty();
    }

    private boolean waitForDeadLetter() {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (rabbitTemplate.receive(queues.deadLetter().queueName()) != null) {
                return true;
            }
            pause();
        }
        return false;
    }

    private Optional<CatalogItemDomain> waitForItem(Predicate<CatalogItemDomain> condition) {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Optional<CatalogItemDomain> found = itemRepository.findById(itemId).filter(condition);
            if (found.isPresent()) {
                return found;
            }
            pause();
        }
        return itemRepository.findById(itemId).filter(condition);
    }

    private void publish(String body, InventoryQueueProperties.Queue queue) {
        rabbitTemplate.convertAndSend(queue.exchangeName(), queue.routingKeyName(), body);
    }

    private static void pause() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String envelope(UUID eventId, String type, UUID itemId, String name) {
        return """
                {
                  "eventId": "%s",
                  "type": "%s",
                  "version": 1,
                  "occurredAt": "2026-10-10T12:00:00Z",
                  "correlationId": "test",
                  "producer": "ReceiverMessagesBrokerTest",
                  "data": {
                    "id": "%s",
                    "name": "%s",
                    "description": "creado por la prueba",
                    "price": 7700,
                    "taxCategory": "STANDARD",
                    "sellable": true,
                    "category": "GENERAL"
                  }
                }
                """.formatted(eventId, type, itemId, name);
    }
}
