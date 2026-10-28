package com.hotelflow.service;

import java.math.BigDecimal;

/**
 * Global configuration settings for HotelFlow.
 */
public class HotelConfig {
    // Academic demo default: 12% GST
    private static BigDecimal taxRate = new BigDecimal("0.12");
    private static final String CURRENCY_SYMBOL = "₹";

    public static BigDecimal getTaxRate() {
        return taxRate;
    }

    public static void setTaxRate(BigDecimal newTaxRate) {
        if (newTaxRate != null && newTaxRate.compareTo(BigDecimal.ZERO) >= 0) {
            taxRate = newTaxRate;
        }
    }

    public static String getCurrencySymbol() {
        return CURRENCY_SYMBOL;
    }
}
