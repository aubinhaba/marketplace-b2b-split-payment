package com.aubin.commons.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("normalizes decimal scale per currency (EUR=2, XOF=0)")
        void normalizes_decimal_scale_per_currency() {
            Money eur = Money.of("10.555", "EUR");
            assertThat(eur.amount()).isEqualByComparingTo("10.56");
            assertThat(eur.currency()).isEqualTo("EUR");

            Money xof = Money.of("1000.5", "XOF");
            assertThat(xof.amount()).isEqualByComparingTo("1001");
            assertThat(xof.currency()).isEqualTo("XOF");
        }

        @Test
        @DisplayName("normalizes currency code to uppercase")
        void normalizes_currency_to_uppercase() {
            Money money = Money.of("10.00", "eur");
            assertThat(money.currency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for unknown currency code")
        void throws_for_invalid_currency() {
            assertThatThrownBy(() -> Money.of("10.00", "INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> Money.of("10.00", "ZZZ"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws NullPointerException when amount is null")
        void throws_when_amount_null() {
            assertThatThrownBy(() -> Money.of((BigDecimal) null, "EUR"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("zero() creates a zero amount for the given currency")
        void zero_creates_zero_amount() {
            Money zero = Money.zero("EUR");
            assertThat(zero.amount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(zero.currency()).isEqualTo("EUR");
            assertThat(zero.isZeroOrNegative()).isTrue();
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("add() returns a new Money without modifying the original")
        void add_returns_new_money() {
            Money original = Money.of("100.00", "EUR");
            Money addend = Money.of("50.00", "EUR");

            Money result = original.add(addend);

            assertThat(original.amount()).isEqualByComparingTo("100.00");
            assertThat(result.amount()).isEqualByComparingTo("150.00");
            assertThat(result).isNotSameAs(original);
        }

        @Test
        @DisplayName("subtract() returns a new Money without modifying the original")
        void subtract_returns_new_money() {
            Money original = Money.of("100.00", "EUR");
            Money result = original.subtract(Money.of("30.00", "EUR"));

            assertThat(original.amount()).isEqualByComparingTo("100.00");
            assertThat(result.amount()).isEqualByComparingTo("70.00");
        }
    }

    @Nested
    @DisplayName("Arithmetic")
    class Arithmetic {

        @ParameterizedTest(name = "{0} EUR + {1} EUR = {2} EUR")
        @CsvSource({
                "10.00, 5.50, 15.50",
                "0.00, 0.01, 0.01",
                "100.00, 0.00, 100.00",
                "99.99, 0.01, 100.00"
        })
        @DisplayName("adds two amounts of the same currency")
        void adds_same_currency(String a, String b, String expected) {
            Money result = Money.of(a, "EUR").add(Money.of(b, "EUR"));
            assertThat(result.amount()).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("subtraction allows negative results (partial refund)")
        void subtraction_allows_negative_result() {
            Money result = Money.of("10.00", "EUR").subtract(Money.of("15.00", "EUR"));
            assertThat(result.amount()).isEqualByComparingTo("-5.00");
            assertThat(result.isZeroOrNegative()).isTrue();
        }

        @Test
        @DisplayName("multiply computes commission (2.5% of 100 EUR = 2.50 EUR)")
        void multiply_computes_commission() {
            Money amount = Money.of("100.00", "EUR");
            Money commission = amount.multiply(new BigDecimal("0.025"));
            assertThat(commission.amount()).isEqualByComparingTo("2.50");
        }

        @Test
        @DisplayName("rejects addition of different currencies")
        void rejects_different_currencies_add() {
            Money eur = Money.of("10.00", "EUR");
            Money usd = Money.of("10.00", "USD");

            assertThatThrownBy(() -> eur.add(usd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("EUR")
                    .hasMessageContaining("USD");
        }

        @Test
        @DisplayName("rejects subtraction of different currencies")
        void rejects_different_currencies_subtract() {
            assertThatThrownBy(() -> Money.of("100.00", "EUR").subtract(Money.of("10.00", "USD")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Comparisons")
    class Comparisons {

        @Test
        @DisplayName("isPositive() is true for amounts > 0")
        void isPositive_true_when_positive() {
            assertThat(Money.of("0.01", "EUR").isPositive()).isTrue();
            assertThat(Money.of("0.00", "EUR").isPositive()).isFalse();
            assertThat(Money.of("-0.01", "EUR").isPositive()).isFalse();
        }

        @Test
        @DisplayName("isGreaterThan() compares two amounts of the same currency")
        void isGreaterThan_compares_amounts() {
            Money hundred = Money.of("100.00", "EUR");
            Money ten = Money.of("10.00", "EUR");

            assertThat(hundred.isGreaterThan(ten)).isTrue();
            assertThat(ten.isGreaterThan(hundred)).isFalse();
        }

        @Test
        @DisplayName("equality is value-based (Value Object)")
        void equality_is_value_based() {
            Money a = Money.of("10.00", "EUR");
            Money b = Money.of("10.00", "EUR");
            Money c = Money.of("10.00", "USD");

            assertThat(a).isEqualTo(b);
            assertThat(a).isNotEqualTo(c);
            assertThat(a).isNotSameAs(b);
        }
    }
}
