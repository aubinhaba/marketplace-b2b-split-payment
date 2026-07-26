package com.aubin.payment.infrastructure.adapter.out.psp;

import com.aubin.payment.application.port.out.PspGateway;
import com.aubin.payment.domain.model.Payment;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

@Component
public class StripeGatewayAdapter implements PspGateway {

    private final double platformFeeRate;

    public StripeGatewayAdapter(@Value("${stripe.platform-fee-rate:0.02}") double platformFeeRate) {
        this.platformFeeRate = platformFeeRate;
    }

    // The fallback belongs on @Retry: on @CircuitBreaker it would swallow the first failure and no retry would ever happen
    // No @TimeLimiter: it only supports CompletableFuture/Mono and would fail this synchronous method before it reaches Stripe
    @Override
    @CircuitBreaker(name = "stripe")
    @Retry(name = "stripe", fallbackMethod = "authorizeFallback")
    @Bulkhead(name = "stripe")
    public Payment authorize(Payment payment) {
        try {
            PaymentIntentCreateParams params = buildParams(payment);
            PaymentIntent intent = PaymentIntent.create(params);
            return mapToPayment(intent, payment);

        } catch (CardException e) {
            // A declined card is a business outcome, not a fault: retrying or tripping the breaker on it would be wrong
            return payment.fail();

        } catch (StripeException e) {
            throw new RuntimeException("Stripe API error [" + e.getCode() + "]: " + e.getMessage(), e);
        }
    }

    Payment authorizeFallback(Payment payment, Throwable ex) {
        return payment.fail();
    }

    private PaymentIntentCreateParams buildParams(Payment payment) {
        return PaymentIntentCreateParams.builder()
                .setAmount(toSmallestUnit(payment.amount(), payment.currency()))
                .setCurrency(payment.currency().toLowerCase())
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                .setTransferData(buildTransferData(payment.sellerId()))
                .setApplicationFeeAmount(calculateApplicationFee(payment.amount(), payment.currency()))
                .build();
    }

    private PaymentIntentCreateParams.TransferData buildTransferData(String sellerId) {
        return PaymentIntentCreateParams.TransferData.builder()
                .setDestination(sellerId)
                .build();
    }

    private long calculateApplicationFee(BigDecimal amount, String currencyCode) {
        int fractionDigits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        BigDecimal fee = amount
                // valueOf not new BigDecimal(double): the latter keeps the binary rounding error of the literal
                .multiply(BigDecimal.valueOf(platformFeeRate))
                .setScale(fractionDigits, RoundingMode.HALF_UP);
        return toSmallestUnit(fee, currencyCode);
    }

    private Payment mapToPayment(PaymentIntent intent, Payment payment) {
        return switch (intent.getStatus()) {
            case "requires_capture", "succeeded" -> payment.authorize();
            default -> payment.fail();
        };
    }

    // Scale comes from the currency, never a fixed x100: XOF and JPY have no minor unit and would be charged 100x
    private long toSmallestUnit(BigDecimal amount, String currencyCode) {
        int fractionDigits = Currency.getInstance(currencyCode).getDefaultFractionDigits();

        if (fractionDigits == 0) {
            return amount.longValue();
        }

        BigDecimal multiplier = BigDecimal.TEN.pow(fractionDigits);
        return amount.multiply(multiplier).longValue();
    }
}
