package ru.otus.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest(classes = JwtSecurityIntegrationTest.TestApplication.class,
        properties = "security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef")
@AutoConfigureMockMvc
class JwtSecurityIntegrationTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private final MockMvc mvc;

    @Autowired
    JwtSecurityIntegrationTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void rejectsMissingInvalidAndExpiredTokens() throws Exception {
        mvc.perform(get("/private")).andExpect(status().isUnauthorized());
        mvc.perform(get("/private").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/private").header("Authorization", "Bearer " + token(Instant.now().minusSeconds(10))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsVerifiedTokenAndUsesItsIdentity() throws Exception {
        mvc.perform(get("/private").header("Authorization", "Bearer " + token(Instant.now().plusSeconds(60))))
                .andExpect(status().isOk()).andExpect(content().string("41"));
    }

    private static String token(Instant expiry) {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder().subject("tester").claim("userId", 41L).claim("email", "tester@example.com")
                .issuedAt(Date.from(now.minusSeconds(1))).expiration(Date.from(expiry)).signWith(key).compact();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({JwtSecurityConfiguration.class, Endpoint.class})
    static class TestApplication {}

    @RestController
    static class Endpoint {
        @GetMapping("/private")
        Long privateEndpoint(@AuthenticationPrincipal AuthenticatedUser user) { return user.userId(); }
    }
}
