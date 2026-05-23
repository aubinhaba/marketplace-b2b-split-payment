package com.aubin.commons.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public final class MoneyUtils {

    private MoneyUtils() {}

    public static BigDecimal round(BigDecimal amount, String currencyCode) {
        int scale = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        return amount.setScale(scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b, String currencyCode) {
        return round(a.add(b), currencyCode);
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b, String currencyCode) {
        return round(a.subtract(b), currencyCode);
    }

    public static BigDecimal multiply(BigDecimal amount, BigDecimal factor, String currencyCode) {
        return round(amount.multiply(factor), currencyCode);
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isZeroOrNegative(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) <= 0;
    }

    public static boolean hasNoDecimals(String currencyCode) {
        return Currency.getInstance(currencyCode).getDefaultFractionDigits() == 0;
    }
}
