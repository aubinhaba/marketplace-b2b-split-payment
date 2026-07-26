package com.aubin.payment.infrastructure.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Value("${stripe.api-key}")
    private String apiKey;

    @Value("${stripe.base-url:}")
    private String baseUrl;

    // @PostConstruct not CommandLineRunner: the SDK key is a static field other beans may read while the context is still starting
    @PostConstruct
    void configureStripe() {
        Stripe.apiKey = apiKey;

        if (baseUrl != null && !baseUrl.isBlank()) {
            Stripe.overrideApiBase(baseUrl);
        }
    }
}
