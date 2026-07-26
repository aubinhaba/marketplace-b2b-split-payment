package com.aubin.payment.infrastructure.adapter.out.cache;

import com.aubin.payment.application.port.out.IdempotencyStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Integration rather than unit: the guarantee under test is Redis' own atomicity, which a mocked template cannot show
@DataRedisTest
@Testcontainers
@Import(RedisIdempotencyAdapter.class)
@DisplayName("RedisIdempotencyAdapter — SETNX against a real Redis")
class RedisIdempotencyAdapterIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private IdempotencyStore idempotencyStore;

    @Test
    @DisplayName("returns true for an event id never seen before")
    void tryMark_returns_true_for_new_event_id() {
        UUID eventId = UUID.randomUUID();

        assertThat(idempotencyStore.tryMark(eventId)).isTrue();
    }

    @Test
    @DisplayName("returns false on redelivery of the same event id")
    void tryMark_returns_false_for_already_seen_event_id() {
        UUID eventId = UUID.randomUUID();

        boolean firstDelivery = idempotencyStore.tryMark(eventId);
        boolean secondDelivery = idempotencyStore.tryMark(eventId);

        assertThat(firstDelivery).isTrue();
        assertThat(secondDelivery).isFalse();
    }

    @Test
    @DisplayName("tracks distinct event ids independently")
    void tryMark_different_event_ids_are_independent() {
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        assertThat(idempotencyStore.tryMark(eventId1)).isTrue();
        assertThat(idempotencyStore.tryMark(eventId2)).isTrue();

        assertThat(idempotencyStore.tryMark(eventId1)).isFalse();
        assertThat(idempotencyStore.tryMark(eventId2)).isFalse();
    }
}
