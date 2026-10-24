package com.uco.ucopetinventory.messaging.inventory;

import java.nio.charset.StandardCharsets;

import com.uco.ucopetinventory.util.gson.MapperJsonObjectJackson;

import com.uco.ucopetinventory.domain.catalog.enums.CatalogItemType;
import com.uco.ucopetinventory.dto.catalog.CatalogItemCommand;
import com.uco.ucopetinventory.dto.messaging.Envelope;
import com.uco.ucopetinventory.dto.stock.StockCommand;
import com.uco.ucopetinventory.repository.messaging.IProcessedMessageRepository;
import com.uco.ucopetinventory.service.catalog.CatalogService;
import com.uco.ucopetinventory.service.stock.StockService;
import com.uco.ucopetinventory.util.UnprocessableCommandException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReceiverMessagesBroker {

    private static final Logger LOG = LoggerFactory.getLogger(ReceiverMessagesBroker.class);

    private final MapperJsonObjectJackson mapperJsonObject;
    private final IProcessedMessageRepository processedMessageRepository;
    private final CatalogService catalogService;
    private final StockService stockService;

    public ReceiverMessagesBroker(MapperJsonObjectJackson mapperJsonObject,
                                    IProcessedMessageRepository processedMessageRepository,
                                    CatalogService catalogService,
                                    StockService stockService) {
        this.mapperJsonObject = mapperJsonObject;
        this.processedMessageRepository = processedMessageRepository;
        this.catalogService = catalogService;
        this.stockService = stockService;
    }

    @RabbitListener(queues = {
            "${inventory.product.save.queue-name}",
            "${inventory.product.deactivate.queue-name}",
            "${inventory.service.save.queue-name}",
            "${inventory.service.deactivate.queue-name}",
            "${inventory.stock.adjust.queue-name}"})
    @Transactional
    public void receive(Message message) {
        Envelope envelope = read(new String(message.getBody(), StandardCharsets.UTF_8));
        if (envelope.eventId() == null || envelope.type() == null) {
            throw new UnprocessableCommandException("El sobre llego sin eventId o sin type");
        }
        if (processedMessageRepository.claim(envelope.eventId(), envelope.type()) == 0) {
            LOG.info("Comando {} ya procesado, se descarta el duplicado: {}", envelope.type(), envelope.eventId());
            return;
        }
        dispatch(envelope);
        LOG.info("Comando {} aplicado: {}", envelope.type(), envelope.eventId());
    }

    private void dispatch(Envelope envelope) {
        switch (envelope.type()) {
            case "inventory.product.create" -> catalogService.create(CatalogItemType.PRODUCT, catalogItem(envelope));
            case "inventory.product.update" -> catalogService.update(CatalogItemType.PRODUCT, catalogItem(envelope));
            case "inventory.product.deactivate" ->
                    catalogService.deactivate(CatalogItemType.PRODUCT, catalogItem(envelope));
            case "inventory.service.create" -> catalogService.create(CatalogItemType.SERVICE, catalogItem(envelope));
            case "inventory.service.update" -> catalogService.update(CatalogItemType.SERVICE, catalogItem(envelope));
            case "inventory.service.deactivate" ->
                    catalogService.deactivate(CatalogItemType.SERVICE, catalogItem(envelope));
            case "inventory.stock.adjust" -> stockService.adjust(stock(envelope));
            default -> throw new UnprocessableCommandException("Tipo de comando desconocido: " + envelope.type());
        }
    }

    private Envelope read(String body) {
        return mapperJsonObject.execute(body, Envelope.class)
                .orElseThrow(() -> new UnprocessableCommandException("El cuerpo no es un sobre valido"));
    }

    private CatalogItemCommand catalogItem(Envelope envelope) {
        return convert(envelope, CatalogItemCommand.class);
    }

    private StockCommand stock(Envelope envelope) {
        return convert(envelope, StockCommand.class);
    }

    private <T> T convert(Envelope envelope, Class<T> type) {
        if (envelope.data() == null) {
            throw new UnprocessableCommandException("El sobre " + envelope.type() + " llego sin data");
        }
        return mapperJsonObject.execute(envelope.data(), type)
                .orElseThrow(() -> new UnprocessableCommandException(
                        "El data de " + envelope.type() + " no tiene la forma esperada"));
    }
}
