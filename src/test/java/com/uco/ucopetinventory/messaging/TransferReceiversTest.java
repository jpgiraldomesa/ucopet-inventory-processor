package com.uco.ucopetinventory.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.uco.ucopetinventory.domain.transfer.TransferDomain;
import com.uco.ucopetinventory.domain.transfer.TransferStatus;
import com.uco.ucopetinventory.repository.product.IProductRepository;
import com.uco.ucopetinventory.repository.transfer.ITransferRepository;
import org.awaitility.core.ConditionTimeoutException;
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
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(InventoryTestQueues.class)
@DisplayName("Los receptores de traslados, de punta a punta")
class TransferReceiversTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL = Duration.ofMillis(300);

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private RabbitAdmin rabbitAdmin;
    @Autowired private Environment environment;
    @Autowired private IProductRepository productRepository;
    @Autowired private ITransferRepository transferRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final UUID productId = UUID.randomUUID();
    private final UUID transferId = UUID.randomUUID();
    private final UUID originId = UUID.randomUUID();
    private final UUID destinationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertPerson(userId);
        insertHeadquarter(originId, "Sede origen prueba traslados");
        insertHeadquarter(destinationId, "Sede destino prueba traslados");

        publish("inventory.product.save", product());
        waitFor(() -> productRepository.findById(productId))
                .orElseThrow(() -> new AssertionError("el producto no llego a la base en " + TIMEOUT));

        // 10 unidades en el origen
        publish("inventory.stock.adjust", stock(originId, 10));
        assertThat(waitFor(() -> quantity(originId).filter(q -> q == 10))).isPresent();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from transfers where id = ?", transferId);
        jdbcTemplate.update("delete from stock_movements where product_id = ?", productId);
        jdbcTemplate.update("delete from stocks where product_id = ?", productId);
        productRepository.findById(productId).ifPresent(productRepository::delete);
        jdbcTemplate.update("delete from headquarter where id in (?, ?)", originId, destinationId);
        // despues de borrar transfers, porque transfers.created_by/updated_by apuntan a persons
        jdbcTemplate.update("delete from persons where id = ?", userId);
    }

    @Test
    @DisplayName("save crea el traslado en PENDING sin tocar el stock")
    void save_createsPendingTransfer() {
        publish("inventory.transfer.save", transfer(originId, destinationId, 4));

        TransferDomain created = waitFor(() -> transferRepository.findById(transferId))
                .orElseThrow(() -> new AssertionError("el traslado no llego a la base en " + TIMEOUT));
        assertThat(created.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(created.getQuantity()).isEqualTo(4);
        assertThat(created.getCreatedBy()).isEqualTo(userId);
        assertThat(quantity(originId)).contains(10);
        assertThat(quantity(destinationId)).isEmpty();
    }

    @Test
    @DisplayName("otro save con el mismo id edita el traslado mientras siga PENDING")
    void save_updatesWhilePending() {
        publish("inventory.transfer.save", transfer(originId, destinationId, 4));
        waitFor(() -> transferRepository.findById(transferId)).orElseThrow();

        publish("inventory.transfer.save", transfer(originId, destinationId, 6));

        assertThat(waitFor(() -> transferRepository.findById(transferId)
                .filter(t -> t.getQuantity() == 6)))
                .as("el segundo save tiene que actualizar la cantidad")
                .isPresent();
    }

    @Test
    @DisplayName("PENDING -> IN_PROGRESS descuenta del origen, y IN_PROGRESS -> COMPLETED suma al destino")
    void status_movesStockAcrossHeadquarters() {
        savePending(4);

        publish("inventory.transfer.status", status("IN_PROGRESS"));
        assertThat(waitFor(() -> statusOf().filter(s -> s == TransferStatus.IN_PROGRESS))).isPresent();
        assertThat(quantity(originId)).contains(6);

        publish("inventory.transfer.status", status("COMPLETED"));
        assertThat(waitFor(() -> statusOf().filter(s -> s == TransferStatus.COMPLETED))).isPresent();
        assertThat(quantity(destinationId)).contains(4);
        assertThat(quantity(originId)).contains(6);
    }

    @Test
    @DisplayName("una transicion invalida (PENDING -> COMPLETED) se descarta sin mover stock")
    void status_invalidTransitionIsDiscarded() {
        savePending(4);

        publish("inventory.transfer.status", status("COMPLETED"));

        waitForEmptyQueue("inventory.transfer.status");
        assertThat(statusOf()).contains(TransferStatus.PENDING);
        assertThat(quantity(originId)).contains(10);
        assertThat(quantity(destinationId)).isEmpty();
    }

    @Test
    @DisplayName("cancelar un traslado PENDING lo cancela sin tocar el stock")
    void cancel_pendingDoesNotTouchStock() {
        savePending(4);

        publish("inventory.transfer.cancel", cancel());

        assertThat(waitFor(() -> statusOf().filter(s -> s == TransferStatus.CANCELLED))).isPresent();
        assertThat(quantity(originId)).contains(10);
    }

    @Test
    @DisplayName("cancelar un traslado IN_PROGRESS devuelve el stock al origen")
    void cancel_inProgressReturnsStockToOrigin() {
        savePending(4);
        publish("inventory.transfer.status", status("IN_PROGRESS"));
        assertThat(waitFor(() -> quantity(originId).filter(q -> q == 6))).isPresent();

        publish("inventory.transfer.cancel", cancel());

        assertThat(waitFor(() -> statusOf().filter(s -> s == TransferStatus.CANCELLED))).isPresent();
        assertThat(quantity(originId)).contains(10);
    }

    @Test
    @DisplayName("no se puede cancelar un traslado COMPLETED")
    void cancel_completedIsDiscarded() {
        savePending(4);
        publish("inventory.transfer.status", status("IN_PROGRESS"));
        waitFor(() -> statusOf().filter(s -> s == TransferStatus.IN_PROGRESS)).orElseThrow();
        publish("inventory.transfer.status", status("COMPLETED"));
        waitFor(() -> statusOf().filter(s -> s == TransferStatus.COMPLETED)).orElseThrow();

        publish("inventory.transfer.cancel", cancel());

        waitForEmptyQueue("inventory.transfer.cancel");
        assertThat(statusOf()).contains(TransferStatus.COMPLETED);
    }

    @Test
    @DisplayName("un save con la misma sede de origen y destino se descarta")
    void save_sameHeadquarterIsDiscarded() {
        publish("inventory.transfer.save", transfer(originId, originId, 4));

        waitForEmptyQueue("inventory.transfer.save");
        assertThat(transferRepository.findById(transferId)).isEmpty();
    }

    @Test
    @DisplayName("un mensaje con JSON incompleto se descarta sin escribir nada")
    void save_incompleteMessageIsDiscarded() {
        publish("inventory.transfer.save", "{\"id\":\"" + transferId + "\"}");

        waitForEmptyQueue("inventory.transfer.save");
        assertThat(transferRepository.findById(transferId)).isEmpty();
    }

    // ---------- Ayudantes ----------

    private void savePending(int quantity) {
        publish("inventory.transfer.save", transfer(originId, destinationId, quantity));
        waitFor(() -> transferRepository.findById(transferId))
                .orElseThrow(() -> new AssertionError("el traslado no llego a la base en " + TIMEOUT));
    }

    private void insertPerson(UUID id) {
        jdbcTemplate.update(
                "insert into persons (id, active, is_admin, document_number, document_type, email, "
                        + "failed_attempts, first_name, last_name, token_version) "
                        + "values (?, true, false, ?, 'CC', ?, 0, 'Prueba', 'Traslados', 0)",
                id,
                String.format("%010d", Math.abs(id.getMostSignificantBits() % 10_000_000_000L)),
                "traslados-" + id + "@prueba.test");
    }

    private void insertHeadquarter(UUID id, String name) {
        jdbcTemplate.update("insert into headquarter (id, name, address, is_active) values (?, ?, ?, true)",
                id, name, "Calle 1");
    }

    private Optional<Integer> quantity(UUID headquarterId) {
        return jdbcTemplate.queryForList(
                "select quantity from stocks where product_id = ? and headquarter_id = ?",
                Integer.class, productId, headquarterId).stream().findFirst();
    }

    private Optional<TransferStatus> statusOf() {
        return transferRepository.findById(transferId).map(TransferDomain::getStatus);
    }

    private void publish(String key, String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("idMensaje", UUID.randomUUID().toString());
        rabbitTemplate.send(environment.getRequiredProperty(key + ".exchange-name"),
                environment.getRequiredProperty(key + ".routing-key-name"),
                MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).andProperties(properties).build());
    }

    private String product() {
        return "{\"id\":\"" + productId + "\",\"name\":\"Producto traslados " + productId + "\","
                + "\"description\":\"creado por la prueba\",\"price\":7700,\"taxCategory\":\"STANDARD\","
                + "\"sellable\":true,\"active\":true,\"category\":\"GENERAL\","
                + "\"providers\":[],\"stockAtLocation\":null}";
    }

    private String stock(UUID headquarterId, int quantity) {
        return "{\"productId\":\"" + productId + "\",\"headquarterId\":\"" + headquarterId
                + "\",\"quantity\":" + quantity + "}";
    }

    private String transfer(UUID origin, UUID destination, int quantity) {
        return "{\"id\":\"" + transferId + "\",\"originHeadquarterId\":\"" + origin
                + "\",\"destinationHeadquarterId\":\"" + destination + "\",\"productId\":\"" + productId
                + "\",\"quantity\":" + quantity + ",\"status\":\"PENDING\","
                + "\"observations\":\"creado por la prueba\",\"performedBy\":\"" + userId + "\"}";
    }

    private String status(String status) {
        return "{\"id\":\"" + transferId + "\",\"status\":\"" + status + "\",\"performedBy\":\"" + userId + "\"}";
    }

    private String cancel() {
        return "{\"id\":\"" + transferId + "\",\"performedBy\":\"" + userId + "\"}";
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