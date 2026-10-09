package com.travel_system.backend_app.model.enums;

import java.time.Duration;

public enum RateLimitPolicy {
    BOOTSTRAP("bootstrap:", 5, Duration.ofMinutes(15), KeyType.IP),
    SENSITIVE_OPERATION("sensitive-operation:", 10, Duration.ofMinutes(15), KeyType.IP),
    REGISTER("register:", 5, Duration.ofHours(1), KeyType.IP),
    VERIFY_EMAIL("verify-email:", 10, Duration.ofMinutes(15), KeyType.IP),
    RESEND_VERIFICATION("resend-verification:", 3, Duration.ofHours(1), KeyType.USER),
    SEND_INVITATION("send-invitation:", 50, Duration.ofHours(1), KeyType.USER);

    private final String keyPrefix;
    private final int maxAttempts;
    private final Duration window;
    private final KeyType keyType;

    RateLimitPolicy(String keyPrefix, int maxAttempts, Duration window, KeyType keyType) {
        this.keyPrefix = keyPrefix;
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.keyType = keyType;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration window() {
        return window;
    }

    public KeyType keyType() {
        return keyType;
    }
}