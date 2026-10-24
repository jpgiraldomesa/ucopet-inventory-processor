package com.uco.ucopetinventory.messaging;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.uco.ucopetinventory.domain.catalog.CatalogItemDomain;
import com.uco.ucopetinventory.domain.catalog.enums.CatalogItemType;
import com.uco.ucopetinventory.repository.catalog.ICatalogItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(InventoryTestQueues.class)
@DisplayName("Los receptores de inventario, de punta a punta")
class InventoryReceiversTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private Environment environment;

    @Autowired
    private ICatalogItemRepository itemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID itemId = UUID.randomUUID();
    private final UUID headquarterId = UUID.randomUUID();

    @BeforeEach
    void createHeadquarter() {
        jdbcTemplate.update("insert into headquarter (id, name, address, is_active) values (?, ?, ?, true)",
                headquarterId, "Sede prueba procesador", "Calle 1");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from stocks where product_id = ?", itemId);
        jdbcTemplate.update("delete from service_headquarters where service_id = ?", itemId);
        itemRepository.deleteById(itemId);
        jdbcTemplate.update("delete from headquarter where id = ?", headquarterId);
    }

    @Test
    @DisplayName("save crea el producto, y otro save con el mismo id lo actualiza sin reactivarlo")
    void productSave_createsThenUpdates() {
        publish("inventory.product.save", item("PRODUCT", "Producto por el bus", 7700));
        CatalogItemDomain created = waitFor(() -> itemRepository.findById(itemId))
                .orElseThrow(() -> new AssertionError("el alta no llego a la base en " + TIMEOUT));
        assertThat(created.getType()).isEqualTo(CatalogItemType.PRODUCT);
        assertThat(created.getActive()).isTrue();
        assertThat(created.getPrice()).isEqualByComparingTo("7700");

        publish("inventory.product.save", item("PRODUCT", "Producto por el bus", 9900));
        assertThat(waitFor(() -> itemRepository.findById(itemId).filter(item -> item.getPrice().compareTo(BigDecimal.valueOf(9900)) == 0)))
                .as("el segundo save tiene que actualizar el precio")
                .isPresent();
    }

    @Test
    @DisplayName("el JSON con tildes llega entero")
    void productSave_keepsAccents() {
        publish("inventory.product.save", item("PRODUCT", "Arnés de ñandú", 7700));

        assertThat(waitFor(() -> itemRepository.findById(itemId)))
                .map(CatalogItemDomain::getName)
                .contains("Arnés de ñandú");
    }

    @Test
    @DisplayName("deactivate deja el producto inactivo")
    void productDeactivation_leavesItInactive() {
        publish("inventory.product.save", item("PRODUCT", "Producto para dar de baja", 7700));
        waitFor(() -> itemRepository.findById(itemId))
                .orElseThrow(() -> new AssertionError("el alta no llego a la base en " + TIMEOUT));

        publish("inventory.product.deactivate", "{\"id\":\"" + itemId + "\",\"type\":\"PRODUCT\",\"active\":false}");

        assertThat(waitFor(() -> itemRepository.findById(itemId).filter(item -> !item.getActive())))
                .as("la baja tiene que dejar el producto inactivo")
                .isPresent();
    }

    @Test
    @DisplayName("save de un servicio guarda sus sedes, y deactivate lo da de baja")
    void serviceSaveAndDeactivation() {
        publish("inventory.service.save", "{\"id\":\"" + itemId + "\",\"type\":\"SERVICE\",\"name\":\"Servicio por el bus\","
                + "\"price\":5000,\"taxCategory\":\"STANDARD\",\"sellable\":true,\"category\":\"GENERAL\","
                + "\"purchasable\":false,\"headquarterIds\":[\"" + headquarterId + "\"]}");
        waitFor(() -> itemRepository.findByIdAndType(itemId, CatalogItemType.SERVICE))
                .orElseThrow(() -> new AssertionError("el servicio no llego a la base en " + TIMEOUT));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from service_headquarters where service_id = ?", Integer.class, itemId))
                .isEqualTo(1);

        publish("inventory.service.deactivate", "{\"id\":\"" + itemId + "\",\"type\":\"SERVICE\",\"active\":false}");

        assertThat(waitFor(() -> itemRepository.findById(itemId).filter(item -> !item.getActive()))).isPresent();
    }

    @Test
    @DisplayName("adjust suma y descuenta, y lo que dejaria el stock en negativo se descarta")
    void stockAdjust_addsSubtractsAndRejectsNegative() {
        publish("inventory.product.save", item("PRODUCT", "Producto con stock", 7700));
        waitFor(() -> itemRepository.findById(itemId))
                .orElseThrow(() -> new AssertionError("el alta no llego a la base en " + TIMEOUT));

        publish("inventory.stock.adjust", stock(10));
        assertThat(waitFor(() -> quantity().filter(quantity -> quantity == 10))).isPresent();

        publish("inventory.stock.adjust", stock(-3));
        assertThat(waitFor(() -> quantity().filter(quantity -> quantity == 7))).isPresent();

        publish("inventory.stock.adjust", stock(-100));
        waitForEmptyQueue("inventory.stock.adjust");
        assertThat(quantity()).contains(7);
    }

    @Test
    @DisplayName("un mensaje que no se puede aplicar se descarta sin escribir nada")
    void invalidMessage_isDiscarded() {
        publish("inventory.product.save", "{\"type\":\"PRODUCT\",\"name\":\"Sin id\"}");
        publish("inventory.product.deactivate", "{\"id\":\"" + itemId + "\",\"type\":\"PRODUCT\",\"active\":false}");

        waitForEmptyQueue("inventory.product.save");
        waitForEmptyQueue("inventory.product.deactivate");
        assertThat(itemRepository.findById(itemId)).isEmpty();
    }

    private Optional<Integer> quantity() {
        return jdbcTemplate.queryForList("select quantity from stocks where product_id = ? and headquarter_id = ?",
                Integer.class, itemId, headquarterId).stream().findFirst();
    }

    private void publish(String key, String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("idMensaje", UUID.randomUUID().toString());
        rabbitTemplate.send(environment.getRequiredProperty(key + ".exchange-name"),
                environment.getRequiredProperty(key + ".routing-key-name"),
                MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).andProperties(properties).build());
    }

    private String item(String type, String name, int price) {
        return "{\"id\":\"" + itemId + "\",\"type\":\"" + type + "\",\"name\":\"" + name + "\","
                + "\"description\":\"creado por la prueba\",\"price\":" + price + ",\"taxCategory\":\"STANDARD\","
                + "\"sellable\":true,\"active\":true,\"category\":\"GENERAL\"}";
    }

    private String stock(int quantity) {
        return "{\"productId\":\"" + itemId + "\",\"headquarterId\":\"" + headquarterId + "\",\"quantity\":" + quantity + "}";
    }

    private <T> Optional<T> waitFor(Supplier<Optional<T>> lookup) {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Optional<T> found = lookup.get();
            if (found.isPresent()) {
                return found;
            }
            pause();
        }
        return lookup.get();
    }

    private void waitForEmptyQueue(String key) {
        String queue = environment.getRequiredProperty(key + ".queue-name");
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            var info = rabbitAdmin.getQueueInfo(queue);
            if (info != null && info.getMessageCount() == 0) {
                pause();
                return;
            }
            pause();
        }
    }

    private static void pause() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
