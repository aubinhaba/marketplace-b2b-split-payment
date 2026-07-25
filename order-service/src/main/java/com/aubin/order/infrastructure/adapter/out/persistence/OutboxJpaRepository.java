package com.aubin.order.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, UUID> {

    // Native query: expressing SKIP LOCKED through JPQL requires a dialect-specific lock-timeout hint
    @Query(
            value = "SELECT * FROM outbox WHERE processed = false ORDER BY created_at ASC LIMIT 50 FOR UPDATE SKIP LOCKED",
            nativeQuery = true
    )
    List<OutboxJpaEntity> findPendingEventsForUpdate();
}
