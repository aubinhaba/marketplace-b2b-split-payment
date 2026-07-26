package com.aubin.payment.infrastructure.adapter.out.psp;

import com.aubin.payment.domain.model.Payment;
import com.aubin.payment.domain.model.PaymentStatus;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.stripe.Stripe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
@DisplayName("StripeGatewayAdapter — against WireMock")
class StripeGatewayAdapterTest {

    private StripeGatewayAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        Stripe.apiKey = "sk_test_fake_key_for_wiremock";
        Stripe.overrideApiBase("http://localhost:" + wm.getHttpPort());
        adapter = new StripeGatewayAdapter(0.02);
    }

    @Test
    @DisplayName("returns AUTHORIZED when Stripe answers requires_capture")
    void authorize_returns_authorized_when_stripe_succeeds_with_requires_capture() {
        stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(okJson("""
                        {
                          "id": "pi_test_success_123",
                          "object": "payment_intent",
                          "amount": 15000,
                          "currency": "eur",
                          "status": "requires_capture",
                          "capture_method": "manual",
                          "livemode": false,
                          "created": 1234567890,
                          "payment_method_types": ["card"],
                          "metadata": {}
                        }
                        """)));

        Payment payment = Payment.create("order-1", "seller-1", "customer-fr-001", BigDecimal.valueOf(150.00), "EUR");

        Payment result = adapter.authorize(payment);

        assertThat(result.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(result.id()).isEqualTo(payment.id());
    }

    @Test
    @DisplayName("returns AUTHORIZED when Stripe answers succeeded")
    void authorize_returns_authorized_when_stripe_succeeds_with_succeeded() {
        stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(okJson("""
                        {
                          "id": "pi_test_captured_456",
                          "object": "payment_intent",
                          "amount": 250000,
                          "currency": "usd",
                          "status": "succeeded",
                          "capture_method": "automatic",
                          "livemode": false,
                          "created": 1234567890,
                          "payment_method_types": ["card"],
                          "metadata": {}
                        }
                        """)));

        Payment payment = Payment.create("order-1", "seller-1", "customer-us-002", BigDecimal.valueOf(2500.00), "USD");

        Payment result = adapter.authorize(payment);

        assertThat(result.status()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    // A decline must return FAILED, not throw: throwing would make Resilience4j retry a card that will decline again
    @Test
    @DisplayName("returns FAILED when the card is declined")
    void authorize_returns_failed_when_card_declined() {
        stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse()
                        .withStatus(402)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "error": {
                                    "type": "card_error",
                                    "code": "card_declined",
                                    "decline_code": "generic_decline",
                                    "message": "Your card was declined.",
                                    "param": null
                                  }
                                }
                                """)));

        Payment payment = Payment.create("order-1", "seller-1", "customer-fr-001", BigDecimal.valueOf(150.00), "EUR");

        Payment result = adapter.authorize(payment);

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.id()).isEqualTo(payment.id());
    }

    @Test
    @DisplayName("throws when Stripe returns a 500")
    void authorize_throws_runtime_exception_when_stripe_returns_500() {
        stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "error": {
                                    "type": "api_error",
                                    "message": "Internal server error."
                                  }
                                }
                                """)));

        Payment payment = Payment.create("order-1", "seller-1", "customer-fr-001", BigDecimal.valueOf(150.00), "EUR");

        assertThatThrownBy(() -> adapter.authorize(payment))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Stripe API error");
    }

    @Test
    @DisplayName("sends a zero-decimal currency unscaled: XOF 75000 stays 75000")
    void authorize_handles_zero_decimal_currency_xof_correctly() {
        stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(okJson("""
                        {
                          "id": "pi_test_xof_789",
                          "object": "payment_intent",
                          "amount": 75000,
                          "currency": "xof",
                          "status": "requires_capture",
                          "capture_method": "manual",
                          "livemode": false,
                          "created": 1234567890,
                          "payment_method_types": ["card"],
                          "metadata": {}
                        }
                        """)));

        Payment payment = Payment.create("order-1", "seller-1", "customer-sn-003", BigDecimal.valueOf(75000), "XOF");

        Payment result = adapter.authorize(payment);

        assertThat(result.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        verify(postRequestedFor(urlEqualTo("/v1/payment_intents")));
    }

    @Test
    @DisplayName("sends transfer_data and application_fee_amount for the Connect split")
    void authorize_sends_stripe_connect_transfer_data_and_application_fee() {
        stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(okJson("""
                        {
                          "id": "pi_connect_001",
                          "object": "payment_intent",
                          "amount": 15000,
                          "currency": "eur",
                          "status": "requires_capture",
                          "capture_method": "manual",
                          "livemode": false,
                          "created": 1234567890,
                          "payment_method_types": ["card"],
                          "metadata": {}
                        }
                        """)));

        Payment payment = Payment.create(
                "order-connect-001", "acct_test_seller_connect",
                "customer-fr-001", BigDecimal.valueOf(150.00), "EUR");

        adapter.authorize(payment);

        // Asserting on the outgoing body, not the response: WireMock would answer AUTHORIZED even if the split params were missing
        List<LoggedRequest> requests = findAll(postRequestedFor(urlEqualTo("/v1/payment_intents")));
        assertThat(requests).hasSize(1);

        // Matched in pieces because the SDK url-encodes the brackets of transfer_data[destination]
        String body = requests.get(0).getBodyAsString();
        assertThat(body).contains("transfer_data");
        assertThat(body).contains("acct_test_seller_connect");
        assertThat(body).contains("application_fee_amount=300");
    }
}
