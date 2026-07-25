package com.aubin.order;

import com.aubin.order.infrastructure.adapter.in.rest.dto.OrderCreatedResponse;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The 24-hour poll delay keeps outbox rows at processed=false so they stay observable
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                "order.outbox.poll-delay-ms=86400000"
        }
)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("PlaceOrder IT — Flyway migrations and outbox atomicity")
class PlaceOrderIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    // Spring Cloud AWS registers an SqsTemplate, so the concrete type is what must be replaced, not SqsOperations
    @MockitoBean
    @SuppressWarnings("unused")
    SqsTemplate sqsTemplate;

    @Autowired
    TestRestTemplate restTemplate;

    // Direct JDBC, bypassing JPA, so assertions see committed state and not Hibernate's cache
    @Autowired
    JdbcTemplate jdbcTemplate;

    // @Transactional would be useless here: each HTTP request commits in its own thread. Children before parents for the FK
    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("DELETE FROM outbox");
        jdbcTemplate.execute("DELETE FROM order_lines");
        jdbcTemplate.execute("DELETE FROM orders");
    }

    private static Map<String, Object> validRequest() {
        return Map.of(
                "customerId", "11111111-1111-1111-1111-111111111111",
                "sellerId", "22222222-2222-2222-2222-222222222222",
                "currency", "EUR",
                "lines", List.of(Map.of(
                        "productId", "prod-widget-001",
                        "productName", "Widget A",
                        "quantity", 2,
                        "unitPrice", new BigDecimal("49.99")
                ))
        );
    }

    @Test
    @DisplayName("Flyway created orders, order_lines and outbox")
    void flyway_appliedAllMigrations() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' "
                        + "  AND table_name IN ('orders', 'order_lines', 'outbox')",
                String.class);

        assertThat(tables).containsExactlyInAnyOrder("orders", "order_lines", "outbox");
    }

    @Nested
    @DisplayName("POST /api/v1/orders")
    class PlaceOrder {

        @Test
        @DisplayName("returns 201 with an id and a matching Location header")
        void placeOrder_returns201WithLocation() {
            ResponseEntity<OrderCreatedResponse> response = restTemplate.postForEntity(
                    "/api/v1/orders", validRequest(), OrderCreatedResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            String orderId = response.getBody().orderId();
            assertThat(orderId).isNotBlank();
            assertThat(response.getHeaders().getLocation())
                    .isNotNull()
                    .hasToString("/api/v1/orders/" + orderId);
        }

        @Test
        @DisplayName("persists the order and its lines")
        void placeOrder_persistsOrderAndLines() {
            ResponseEntity<OrderCreatedResponse> response = restTemplate.postForEntity(
                    "/api/v1/orders", validRequest(), OrderCreatedResponse.class);

            UUID orderId = UUID.fromString(response.getBody().orderId());

            Integer orderCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE id = ? AND status = 'PLACED'",
                    Integer.class, orderId);
            assertThat(orderCount).isEqualTo(1);

            Integer lineCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM order_lines WHERE order_id = ?",
                    Integer.class, orderId);
            assertThat(lineCount).isEqualTo(1);
        }

        @Test
        @DisplayName("writes the outbox event in the same transaction (ADR-003)")
        void placeOrder_writesOutboxEventAtomically() {
            ResponseEntity<OrderCreatedResponse> response = restTemplate.postForEntity(
                    "/api/v1/orders", validRequest(), OrderCreatedResponse.class);

            String orderId = response.getBody().orderId();

            Integer outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbox "
                            + "WHERE aggregate_id = ? AND processed = false AND event_type = 'order.placed'",
                    Integer.class, orderId);

            assertThat(outboxCount)
                    .as("one unprocessed outbox event is expected for order %s", orderId)
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("serializes the payload with ISO-8601 timestamps, not Unix epochs")
        void placeOrder_outboxPayloadUsesIso8601() {
            restTemplate.postForEntity("/api/v1/orders", validRequest(), OrderCreatedResponse.class);

            String payload = jdbcTemplate.queryForObject(
                    "SELECT payload FROM outbox WHERE event_type = 'order.placed'",
                    String.class);

            assertThat(payload)
                    .contains("\"eventId\"")
                    .contains("\"occurredOn\"")
                    .contains("\"customerId\"")
                    .contains("\"sellerId\"")
                    .contains("\"total\"")
                    .doesNotContain("1705328");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{id}")
    class GetOrder {

        @Test
        @DisplayName("returns 200 with the persisted order")
        void getOrder_returns200() {
            ResponseEntity<OrderCreatedResponse> postResponse = restTemplate.postForEntity(
                    "/api/v1/orders", validRequest(), OrderCreatedResponse.class);
            String orderId = postResponse.getBody().orderId();

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> getResponse = restTemplate.getForEntity(
                    "/api/v1/orders/" + orderId, (Class<Map<String, Object>>) (Class<?>) Map.class);

            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            Map<String, Object> body = getResponse.getBody();
            assertThat(body)
                    .containsEntry("orderId", orderId)
                    .containsEntry("customerId", "11111111-1111-1111-1111-111111111111")
                    .containsEntry("sellerId", "22222222-2222-2222-2222-222222222222")
                    .containsEntry("status", "PLACED")
                    .containsKey("lines");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0))
                    .containsEntry("productId", "prod-widget-001")
                    .containsEntry("productName", "Widget A")
                    .containsEntry("quantity", 2);
        }

        @Test
        @DisplayName("returns a 404 ProblemDetail for an unknown order")
        void getOrder_returns404() {
            String unknownId = "00000000-0000-0000-0000-000000000099";

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(
                    "/api/v1/orders/" + unknownId, (Class<Map<String, Object>>) (Class<?>) Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).containsEntry("status", 404);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/orders — validation")
    class Validation {

        @Test
        @DisplayName("returns 400 and persists nothing when customerId is missing")
        void placeOrder_returns400WhenCustomerIdMissing() {
            Map<String, Object> invalidBody = Map.of(
                    "sellerId", "22222222-2222-2222-2222-222222222222",
                    "currency", "EUR",
                    "lines", List.of(Map.of(
                            "productId", "prod-001",
                            "productName", "Widget A",
                            "quantity", 1,
                            "unitPrice", new BigDecimal("10.00")
                    ))
            );

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                    "/api/v1/orders", invalidBody, (Class<Map<String, Object>>) (Class<?>) Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("status", 400);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("returns 400 when the line list is empty")
        void placeOrder_returns400WhenLinesEmpty() {
            Map<String, Object> invalidBody = Map.of(
                    "customerId", "11111111-1111-1111-1111-111111111111",
                    "sellerId", "22222222-2222-2222-2222-222222222222",
                    "currency", "EUR",
                    "lines", List.of()
            );

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                    "/api/v1/orders", invalidBody, (Class<Map<String, Object>>) (Class<?>) Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
