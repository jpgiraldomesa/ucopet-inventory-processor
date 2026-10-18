package com.uco.ucopetinventory.repository.messaging;

import com.uco.ucopetinventory.domain.messaging.ProcessedMessage;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IProcessedMessageRepository extends JpaRepository<ProcessedMessage, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO inventory_processed_messages (event_id, type, processed_at) "
            + "VALUES (:eventId, :type, now()) ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int claim(@Param("eventId") UUID eventId, @Param("type") String type);
}
