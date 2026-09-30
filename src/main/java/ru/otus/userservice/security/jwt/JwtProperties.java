package ru.otus.userservice.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@ConfigurationProperties("security.jwt")
public record JwtProperties(
        String secret,
        @DurationUnit(ChronoUnit.MILLIS) Duration expiration
) {

    private static final int MINIMUM_SECRET_LENGTH = 32;

    public JwtProperties {
        if (secret == null || secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalArgumentException(
                    "security.jwt.secret must contain at least 32 characters"
            );
        }
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            throw new IllegalArgumentException(
                    "security.jwt.expiration must be greater than zero"
            );
        }
    }
}
