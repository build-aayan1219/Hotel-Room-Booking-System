package com.hotelflow.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager for Razorpay Payment Gateway integration.
 * Retrieves API credentials strictly from environment variables or system properties.
 * Never hardcodes API keys or secrets in source code.
 */
public class RazorpayConfig {

    public static final String ENV_KEY_ID = "RAZORPAY_KEY_ID";
    public static final String ENV_KEY_SECRET = "RAZORPAY_KEY_SECRET";
    public static final String PROP_KEY_ID = "razorpay.key.id";
    public static final String PROP_KEY_SECRET = "razorpay.key.secret";

    public static final String CURRENCY_INR = "INR";
    public static final String MERCHANT_NAME = "HotelFlow - Hotel Booking System";
    public static final String THEME_COLOR = "#2563eb"; // Royal Blue matching HotelFlow design

    private static String cachedKeyId = null;
    private static String cachedKeySecret = null;
    private static boolean configLoaded = false;

    private static synchronized void loadConfig() {
        if (configLoaded) return;

        // 1. Check System Properties first (e.g. -Drazorpay.key.id=...)
        cachedKeyId = System.getProperty(PROP_KEY_ID);
        cachedKeySecret = System.getProperty(PROP_KEY_SECRET);

        // 2. Check Environment Variables (e.g. export RAZORPAY_KEY_ID=...)
        if (cachedKeyId == null || cachedKeyId.isBlank()) {
            cachedKeyId = System.getenv(ENV_KEY_ID);
        }
        if (cachedKeySecret == null || cachedKeySecret.isBlank()) {
            cachedKeySecret = System.getenv(ENV_KEY_SECRET);
        }

        // 3. Fallback: Check local config.properties or .env if present
        if (cachedKeyId == null || cachedKeySecret == null) {
            File propFile = new File("config.properties");
            if (propFile.exists()) {
                try (InputStream in = new FileInputStream(propFile)) {
                    Properties p = new Properties();
                    p.load(in);
                    if (cachedKeyId == null) cachedKeyId = p.getProperty(ENV_KEY_ID, p.getProperty(PROP_KEY_ID));
                    if (cachedKeySecret == null) cachedKeySecret = p.getProperty(ENV_KEY_SECRET, p.getProperty(PROP_KEY_SECRET));
                } catch (Exception ignored) {}
            }
        }

        if (cachedKeyId == null || cachedKeySecret == null) {
            File envFile = new File(".env");
            if (envFile.exists()) {
                try (InputStream in = new FileInputStream(envFile)) {
                    Properties p = new Properties();
                    p.load(in);
                    if (cachedKeyId == null) cachedKeyId = p.getProperty(ENV_KEY_ID);
                    if (cachedKeySecret == null) cachedKeySecret = p.getProperty(ENV_KEY_SECRET);
                } catch (Exception ignored) {}
            }
        }

        if (cachedKeyId != null) cachedKeyId = cachedKeyId.trim();
        if (cachedKeySecret != null) cachedKeySecret = cachedKeySecret.trim();

        configLoaded = true;
    }

    public static synchronized void setCredentials(String keyId, String keySecret) {
        cachedKeyId = keyId != null ? keyId.trim() : null;
        cachedKeySecret = keySecret != null ? keySecret.trim() : null;
        configLoaded = true;
    }

    public static synchronized void reload() {
        configLoaded = false;
        loadConfig();
    }

    public static String getKeyId() {
        loadConfig();
        return cachedKeyId != null ? cachedKeyId : "";
    }

    public static String getKeySecret() {
        loadConfig();
        return cachedKeySecret != null ? cachedKeySecret : "";
    }

    public static boolean isConfigured() {
        loadConfig();
        return cachedKeyId != null && !cachedKeyId.isBlank()
                && cachedKeySecret != null && !cachedKeySecret.isBlank();
    }

    public static boolean isTestMode() {
        loadConfig();
        return cachedKeyId != null && cachedKeyId.startsWith("rzp_test_");
    }

    public static String getMaskedKeyId() {
        String key = getKeyId();
        if (key.length() <= 8) return key;
        return key.substring(0, 8) + "..." + key.substring(key.length() - 4);
    }
}
