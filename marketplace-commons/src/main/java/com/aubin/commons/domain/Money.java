package com.aubin.commons.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

    /**
     * Normalizes on construction: amount is rounded HALF_UP to the ISO 4217 scale of currency
     * (EUR=2, XOF=0, BHD=3) and currency is uppercased, so what is read back is not always what was passed.
     *
     * @throws NullPointerException if amount or currency is null
     * @throws IllegalArgumentException if currency is not a known ISO 4217 code
     */
    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");

        Currency curr = Currency.getInstance(currency.toUpperCase());
        amount = amount.setScale(curr.getDefaultFractionDigits(), RoundingMode.HALF_UP);
        currency = currency.toUpperCase();
    }

    /** @throws NumberFormatException if amount is not a valid decimal representation */
    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /** @throws IllegalArgumentException if the currencies differ */
    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Allows a negative result: partial refunds rely on it.
     *
     * @throws IllegalArgumentException if the currencies differ
     */
    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    /** Lossy: no currency check (the factor is scalar) and the result is re-rounded to the currency scale. */
    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isZeroOrNegative() {
        return amount.compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * Partial predicate, not total.
     *
     * @throws IllegalArgumentException if the currencies differ
     */
    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    /**
     * Partial predicate, not total.
     *
     * @throws IllegalArgumentException if the currencies differ
     */
    public boolean isGreaterThanOrEqualTo(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(String.format(
                    "Cannot operate on different currencies: %s and %s. Explicit conversion required.",
                    this.currency, other.currency
            ));
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
