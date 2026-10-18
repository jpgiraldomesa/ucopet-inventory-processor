package com.uco.ucopetinventory.domain.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_processed_messages")
public class ProcessedMessage {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "type", nullable = false, length = 80)
    private String type;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    protected ProcessedMessage() {
    }

    public ProcessedMessage(final UUID eventId, final String type, final OffsetDateTime processedAt) {
        this.eventId = eventId;
        this.type = type;
        this.processedAt = processedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }
}
