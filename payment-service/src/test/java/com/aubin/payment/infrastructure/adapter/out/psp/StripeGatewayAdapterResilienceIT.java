package com.aubin.payment.infrastructure.adapter.out.psp;

import com.aubin.payment.domain.model.Payment;
import com.aubin.payment.domain.model.PaymentStatus;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:resilience_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",

        "stripe.api-key=sk_test_fake_for_resilience_test",
        "stripe.platform-fee-rate=0.02",

        // Production thresholds need too many calls to trip inside a test, so they are lowered here
        "resilience4j.circuitbreaker.instances.stripe.sliding-window-size=2",
        "resilience4j.circuitbreaker.instances.stripe.minimum-number-of-calls=2",
        "resilience4j.circuitbreaker.instances.stripe.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.stripe.wait-duration-in-open-state=1s",

        "resilience4j.retry.instances.stripe.wait-duration=10ms",
        "resilience4j.retry.instances.stripe.max-attempts=3",
        "resilience4j.retry.instances.stripe.enable-exponential-backoff=false",
})
@DisplayName("StripeGatewayAdapter — Resilience4j circuit breaker and retry")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StripeGatewayAdapterResilienceIT {

    // @RegisterExtension static, not @WireMockTest: WireMock must be up before Spring so @DynamicPropertySource can read its port
    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideStripeBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("stripe.base-url", wm::baseUrl);
    }

    @Autowired
    private StripeGatewayAdapter adapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final String STRIPE_500_BODY = """
            {
              "error": {
                "type": "api_error",
                "message": "Internal server error."
              }
            }
            """;

    private static final String STRIPE_SUCCESS_BODY = """
            {
              "id": "pi_test_success",
              "object": "payment_intent",
              "amount": 10000,
              "currency": "eur",
              "status": "requires_capture",
              "capture_method": "manual",
              "livemode": false,
              "created": 1234567890,
              "payment_method_types": ["card"],
              "metadata": {}
            }
            """;

    private Payment makePayment() {
        return Payment.create("order-resilience-test", "seller-test", "customer-test",
                BigDecimal.valueOf(100.00), "EUR");
    }

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("stripe").reset();
        wm.resetAll();
    }

    @Test
    @Order(1)
    @DisplayName("stops retrying once the breaker opens mid-retry, then falls back to FAILED")
    void authorize_retries_until_circuit_opens_then_fallback() {
        wm.stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody(STRIPE_500_BODY)));

        Payment result = adapter.authorize(makePayment());

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);

        // Two, not three: Retry wraps the breaker, so each attempt is recorded and attempt 3 is short-circuited
        wm.verify(2, postRequestedFor(urlEqualTo("/v1/payment_intents")));
    }

    @Test
    @Order(2)
    @DisplayName("opens the breaker from a single authorize call")
    void circuit_breaker_opens_after_minimum_number_of_calls_reached() {
        wm.stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody(STRIPE_500_BODY)));

        adapter.authorize(makePayment());

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("stripe");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @Order(3)
    @DisplayName("fails fast without touching Stripe while the breaker is open")
    void circuit_breaker_open_returns_failed_without_calling_stripe() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("stripe");
        cb.transitionToOpenState();

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Payment result = adapter.authorize(makePayment());

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        wm.verify(0, postRequestedFor(urlEqualTo("/v1/payment_intents")));
    }

    @Test
    @Order(4)
    @DisplayName("calls Stripe exactly once when the breaker is closed and Stripe answers")
    void authorize_succeeds_when_circuit_is_closed() {
        wm.stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(okJson(STRIPE_SUCCESS_BODY)));

        Payment result = adapter.authorize(makePayment());

        assertThat(result.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        wm.verify(1, postRequestedFor(urlEqualTo("/v1/payment_intents")));
    }
}
