package com.uco.ucopetinventory.messaging;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.uco.ucopetinventory.domain.product.ProductDomain;
import com.uco.ucopetinventory.repository.product.IProductRepository;
import com.uco.ucopetinventory.repository.product.IServiceRepository;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
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
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(InventoryTestQueues.class)
@DisplayName("Los receptores de inventario, de punta a punta")
class InventoryReceiversTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL = Duration.ofMillis(300);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private Environment environment;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private IServiceRepository serviceRepository;

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
        jdbcTemplate.update("delete from stock_movements where product_id = ?", itemId);
        jdbcTemplate.update("delete from stocks where product_id = ?", itemId);
        jdbcTemplate.update("delete from service_headquarters where service_id = ?", itemId);
        productRepository.findById(itemId).ifPresent(productRepository::delete);
        serviceRepository.findById(itemId).ifPresent(serviceRepository::delete);
        jdbcTemplate.update("delete from headquarter where id = ?", headquarterId);
    }

    @Test
    @DisplayName("save crea el producto, y otro save con el mismo id lo actualiza")
    void productSave_createsThenUpdates() {
        publish("inventory.product.save", product(named("Producto por el bus"), 7700));
        ProductDomain created = waitFor(() -> productRepository.findById(itemId))
                .orElseThrow(() -> new AssertionError("el alta no llego a la base en " + TIMEOUT));
        assertThat(created.getActive()).isTrue();
        assertThat(created.getPrice()).isEqualByComparingTo("7700");

        publish("inventory.product.save", product(named("Producto por el bus"), 9900));
        BigDecimal updatedPrice = new BigDecimal("9900");
        assertThat(waitFor(() -> productRepository.findById(itemId)
                .filter(item -> item.getPrice().compareTo(updatedPrice) == 0)))
                .as("el segundo save tiene que actualizar el precio")
                .isPresent();
    }

    @Test
    @DisplayName("el JSON con tildes llega entero")
    void productSave_keepsAccents() {
        publish("inventory.product.save", product(named("Arnés de ñandú"), 7700));

        assertThat(waitFor(() -> productRepository.findById(itemId)))
                .map(ProductDomain::getName)
                .contains(named("Arnés de ñandú"));
    }

    @Test
    @DisplayName("el precio guarda los centavos tal cual")
    void productSave_keepsTwoDecimals() {
        publish("inventory.product.save", product(named("Producto con centavos"), 7700)
                .replace("\"price\":7700", "\"price\":25000.50"));

        ProductDomain created = waitFor(() -> productRepository.findById(itemId))
                .orElseThrow(() -> new AssertionError("el alta no llego a la base en " + TIMEOUT));
        assertThat(created.getPrice()).isEqualByComparingTo("25000.50");
    }

    @Test
    @DisplayName("deactivate deja el producto inactivo")
    void productDeactivation_leavesItInactive() {
        publish("inventory.product.save", product(named("Producto para dar de baja"), 7700));
        waitFor(() -> productRepository.findById(itemId))
                .orElseThrow(() -> new AssertionError("el alta no llego a la base en " + TIMEOUT));

        publish("inventory.product.deactivate", onlyId());

        assertThat(waitFor(() -> productRepository.findById(itemId).filter(item -> !item.getActive())))
                .as("la baja tiene que dejar el producto inactivo")
                .isPresent();
    }

    @Test
    @DisplayName("si la baja pide respuesta, contesta aplicado y ya esta en la base, o rechazado si no existe")
    void productDeactivation_repliesWhenAsked() {
        publish("inventory.product.save", product(named("Producto con baja confirmada"), 7700));
        waitFor(() -> productRepository.findById(itemId))
                .orElseThrow(() -> new AssertionError("el alta no llego a la base en " + TIMEOUT));

        Message applied = ask("inventory.product.deactivate", onlyId());
        assertThat(header(applied, "resultado")).isEqualTo("aplicado");
        assertThat(productRepository.findById(itemId))
                .hasValueSatisfying(item -> assertThat(item.getActive()).isFalse());

        Message rejected = ask("inventory.product.deactivate", "{\"id\":\"" + UUID.randomUUID() + "\"}");
        assertThat(header(rejected, "resultado")).isEqualTo("rechazado");
    }

    @Test
    @DisplayName("un ajuste a un producto dado de baja se rechaza aunque la API lo haya dejado pasar")
    void stockAdjust_rejectsDeactivatedProduct() {
        publish("inventory.product.save", product(named("Producto que se da de baja"), 7700));
        waitFor(() -> productRepository.findById(itemId))
                .orElseThrow(() -> new AssertionError("el alta no llego a la base en " + TIMEOUT));
        assertThat(header(ask("inventory.product.deactivate", onlyId()), "resultado")).isEqualTo("aplicado");

        Message rejected = ask("inventory.stock.adjust", stock(3));

        assertThat(header(rejected, "resultado")).isEqualTo("rechazado");
        assertThat(header(rejected, "motivo")).isEqualTo("No se puede ajustar el stock de un producto dado de baja");
        assertThat(quantity()).isEmpty();
    }

    @Test
    @DisplayName("save de un servicio guarda sus sedes, y deactivate lo da de baja")
    void serviceSaveAndDeactivation() {
        publish("inventory.service.save", "{\"id\":\"" + itemId + "\","
                + "\"name\":\"" + named("Servicio por el bus") + "\","
                + "\"price\":5000,\"taxCategory\":\"STANDARD\",\"sellable\":true,\"active\":true,"
                + "\"category\":\"GENERAL\",\"purchasable\":false,\"headquarterIds\":[\"" + headquarterId + "\"]}");
        waitFor(() -> serviceRepository.findById(itemId))
                .orElseThrow(() -> new AssertionError("el servicio no llego a la base en " + TIMEOUT));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from service_headquarters where service_id = ?", Integer.class, itemId))
                .isEqualTo(1);

        publish("inventory.service.deactivate", onlyId());

        assertThat(waitFor(() -> serviceRepository.findById(itemId).filter(item -> !item.getActive()))).isPresent();
    }

    @Test
    @DisplayName("si el ajuste pide respuesta, contesta aplicado con la cantidad o rechazado con el motivo")
    void stockAdjust_repliesWhenAsked() {
        publish("inventory.product.save", product(named("Producto con respuesta"), 7700));
        waitFor(() -> productRepository.findById(itemId))
                .orElseThrow(() -> new AssertionError("el alta no llego a la base en " + TIMEOUT));

        Message applied = ask("inventory.stock.adjust", stock(4));
        assertThat(header(applied, "resultado")).isEqualTo("aplicado");
        assertThat(header(applied, "cantidad")).isEqualTo("4");

        Message rejected = ask("inventory.stock.adjust", stock(-10));
        assertThat(header(rejected, "resultado")).isEqualTo("rechazado");
        assertThat(header(rejected, "motivo")).isEqualTo("La operación dejaría el stock en negativo");
        assertThat(quantity()).contains(4);
    }

    @Test
    @DisplayName("cada ajuste deja su movimiento con el saldo que quedo, y lo rechazado no deja nada")
    void stockAdjust_writesOneMovementPerChange() {
        publish("inventory.product.save", product(named("Producto con historial"), 7700));
        waitFor(() -> productRepository.findById(itemId))
                .orElseThrow(() -> new AssertionError("el alta no llego a la base en " + TIMEOUT));

        assertThat(header(ask("inventory.stock.adjust", stock(5)), "resultado")).isEqualTo("aplicado");
        assertThat(header(ask("inventory.stock.adjust", stock(-2, "SALE")), "resultado")).isEqualTo("aplicado");
        assertThat(header(ask("inventory.stock.adjust", stock(-10, "SALE")), "resultado")).isEqualTo("rechazado");

        assertThat(movements()).containsExactly("5|5|ADJUSTMENT", "-2|3|SALE");
        assertThat(quantity()).contains(3);
    }

    @Test
    @DisplayName("el stock que habia antes del historial queda como saldo inicial, y la suma da el saldo")
    void stockAdjust_recordsOpeningBalanceForOlderRows() {
        publish("inventory.product.save", product(named("Producto anterior al historial"), 7700));
        waitFor(() -> productRepository.findById(itemId))
                .orElseThrow(() -> new AssertionError("el alta no llego a la base en " + TIMEOUT));
        jdbcTemplate.update("insert into stocks (id, product_id, headquarter_id, quantity) values (?, ?, ?, 7)",
                UUID.randomUUID(), itemId, headquarterId);

        assertThat(header(ask("inventory.stock.adjust", stock(1)), "resultado")).isEqualTo("aplicado");

        assertThat(movements()).containsExactly("7|7|OPENING", "1|8|ADJUSTMENT");
        assertThat(jdbcTemplate.queryForObject("select sum(quantity) from stock_movements where product_id = ?",
                Integer.class, itemId)).isEqualTo(8);
    }

    @Test
    @DisplayName("si la API ya dejo de esperar, el ajuste se deshace y el stock queda como estaba")
    void stockAdjust_afterDeadlineIsRolledBack() {
        publish("inventory.product.save", product(named("Producto con plazo"), 7700));
        waitFor(() -> productRepository.findById(itemId))
                .orElseThrow(() -> new AssertionError("el alta no llego a la base en " + TIMEOUT));
        assertThat(header(ask("inventory.stock.adjust", stock(4)), "resultado")).isEqualTo("aplicado");

        Message late = ask("inventory.stock.adjust", stock(3), System.currentTimeMillis() - 1);

        assertThat(header(late, "resultado")).isEqualTo("rechazado");
        assertThat(header(late, "motivo")).isEqualTo("La API dejó de esperar la respuesta: el cambio no se aplicó");
        assertThat(quantity()).contains(4);
        assertThat(movements()).containsExactly("4|4|ADJUSTMENT");
    }

    @Test
    @DisplayName("si la API ya dejo de esperar, el alta se deshace y no queda en la base")
    void productSave_afterDeadlineIsRolledBack() {
        Message late = ask("inventory.product.save", product(named("Producto tardio"), 7700),
                System.currentTimeMillis() - 1);

        assertThat(header(late, "resultado")).isEqualTo("rechazado");
        assertThat(productRepository.findById(itemId)).isEmpty();
    }

    @Test
    @DisplayName("save con respuesta: el mismo nombre en otras mayusculas se rechaza como nombre repetido")
    void productSave_duplicateNameIsRejectedWithReply() {
        String name = "Nombre unico " + itemId;
        Message first = ask("inventory.product.save", product(name, 7700));
        assertThat(header(first, "resultado")).isEqualTo("aplicado");

        UUID other = UUID.randomUUID();
        String sameNameOtherId = product(name.toUpperCase(), 7700).replace(itemId.toString(), other.toString());
        Message second = ask("inventory.product.save", sameNameOtherId);
        assertThat(header(second, "resultado")).isEqualTo("rechazado");
        assertThat(header(second, "tipo")).isEqualTo("nombre-repetido");
        assertThat(productRepository.findById(other)).isEmpty();
    }

    @Test
    @DisplayName("adjust suma y descuenta, y lo que dejaria el stock en negativo se descarta")
    void stockAdjust_addsSubtractsAndRejectsNegative() {
        publish("inventory.product.save", product(named("Producto con stock"), 7700));
        waitFor(() -> productRepository.findById(itemId))
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
        publish("inventory.product.save", "{\"name\":\"Sin id\"}");
        publish("inventory.product.deactivate", onlyId());

        waitForEmptyQueue("inventory.product.save");
        waitForEmptyQueue("inventory.product.deactivate");
        assertThat(productRepository.findById(itemId)).isEmpty();
    }

    private Optional<Integer> quantity() {
        return jdbcTemplate.queryForList("select quantity from stocks where product_id = ? and headquarter_id = ?",
                Integer.class, itemId, headquarterId).stream().findFirst();
    }

    private static String header(Message message, String key) {
        return String.valueOf(message.getMessageProperties().<Object>getHeader(key));
    }

    private Message ask(String key, String json) {
        return ask(key, json, null);
    }

    private Message ask(String key, String json, Long deadline) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("idMensaje", UUID.randomUUID().toString());
        if (deadline != null) {
            properties.setHeader("plazo", deadline);
        }
        Message reply = rabbitTemplate.sendAndReceive(environment.getRequiredProperty(key + ".exchange-name"),
                environment.getRequiredProperty(key + ".routing-key-name"),
                MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).andProperties(properties).build());
        assertThat(reply).as("el procesador tiene que responder").isNotNull();
        return reply;
    }

    private void publish(String key, String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("idMensaje", UUID.randomUUID().toString());
        rabbitTemplate.send(environment.getRequiredProperty(key + ".exchange-name"),
                environment.getRequiredProperty(key + ".routing-key-name"),
                MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).andProperties(properties).build());
    }

    private String named(String base) {
        return base + " " + itemId;
    }

    private String product(String name, int price) {
        return "{\"id\":\"" + itemId + "\",\"name\":\"" + name + "\","
                + "\"description\":\"creado por la prueba\",\"price\":" + price + ",\"taxCategory\":\"STANDARD\","
                + "\"sellable\":true,\"active\":true,\"category\":\"GENERAL\","
                + "\"providers\":[],\"stockAtLocation\":null}";
    }

    private String onlyId() {
        return "{\"id\":\"" + itemId + "\",\"active\":false}";
    }

    private String stock(int quantity) {
        return "{\"productId\":\"" + itemId + "\",\"headquarterId\":\"" + headquarterId
                + "\",\"quantity\":" + quantity + "}";
    }

    private String stock(int quantity, String reason) {
        return "{\"productId\":\"" + itemId + "\",\"headquarterId\":\"" + headquarterId
                + "\",\"quantity\":" + quantity + ",\"reason\":\"" + reason + "\"}";
    }

    private List<String> movements() {
        return jdbcTemplate.queryForList("select quantity || '|' || balance_after || '|' || reason from stock_movements"
                + " where product_id = ? order by occurred_at", String.class, itemId);
    }

    private <T> Optional<T> waitFor(Supplier<Optional<T>> lookup) {
        try {
            return await().atMost(TIMEOUT).pollInterval(POLL).until(lookup::get, Optional::isPresent);
        } catch (ConditionTimeoutException _) {
            return lookup.get();
        }
    }

    private void waitForEmptyQueue(String key) {
        String queue = environment.getRequiredProperty(key + ".queue-name");
        await().atMost(TIMEOUT).pollInterval(POLL).until(() -> {
            var info = rabbitAdmin.getQueueInfo(queue);
            return info != null && info.getMessageCount() == 0;
        });
        await().pollDelay(POLL).until(() -> true);
    }
}
