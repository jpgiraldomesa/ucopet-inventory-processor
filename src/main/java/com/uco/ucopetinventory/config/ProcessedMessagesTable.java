package com.uco.ucopetinventory.config;

import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
public class ProcessedMessagesTable implements CommandLineRunner {

    private final EntityManager entityManager;

    public ProcessedMessagesTable(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(String... args) {
        entityManager.createNativeQuery("""
                CREATE TABLE IF NOT EXISTS inventory_processed_messages (
                    event_id     uuid PRIMARY KEY,
                    type         varchar(80) NOT NULL,
                    processed_at timestamptz NOT NULL DEFAULT now()
                )
                """).executeUpdate();
    }
}
