package ru.otus.userservice.security.jwt;

import org.junit.jupiter.api.Test;
import ru.otus.userservice.entity.User;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET =
            "01234567890123456789012345678901";

    private final JwtService jwtService = new JwtService(
            new JwtProperties(SECRET, Duration.ofHours(1)),
            Clock.systemUTC()
    );

    @Test
    void shouldExtractUsernameFromValidToken() {
        String token = jwtService.generateToken(user());

        assertThat(jwtService.extractValidUsername(token))
                .contains("alice");
    }

    @Test
    void shouldRejectMalformedToken() {
        assertThat(jwtService.extractValidUsername("not-a-token")).isEmpty();
    }

    private User user() {
        return new User(
                1L,
                "alice",
                "Alice",
                "Smith",
                "alice@example.com",
                null,
                "password-hash"
        );
    }
}
