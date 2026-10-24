package com.uco.ucopetinventory.dto.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record Envelope(
        UUID eventId,
        String type,
        Integer version,
        OffsetDateTime occurredAt,
        String correlationId,
        String producer,
        JsonNode data) {
}
