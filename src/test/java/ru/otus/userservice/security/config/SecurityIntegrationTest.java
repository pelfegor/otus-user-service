package ru.otus.userservice.security.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.otus.userservice.controller.ProfileController;
import ru.otus.userservice.service.ProfileService;
import ru.otus.userservice.security.service.UserAuthorizationService;
import ru.otus.userservice.security.jwt.JwtAuthenticationFilter;
import ru.otus.userservice.security.jwt.JwtService;
import ru.otus.userservice.security.handler.SecurityErrorHandler;
import ru.otus.userservice.controller.UserController;
import ru.otus.userservice.dto.UserDetails;
import ru.otus.userservice.service.UserService;
import ru.otus.userservice.entity.User;
import ru.otus.userservice.repository.UserRepository;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        ProfileController.class,
        UserController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtService.class,
        SecurityErrorHandler.class,
        UserAuthorizationService.class
})
@TestPropertySource(properties = {
        "security.jwt.secret=01234567890123456789012345678901",
        "security.jwt.expiration=3600000"
})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private UserService userService;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = new User(
                1L,
                "user1",
                "Ivan",
                "Ivanov",
                "user1@test.ru",
                "+79990000001",
                "password-hash"
        );

        user2 = new User(
                2L,
                "user2",
                "Petr",
                "Petrov",
                "user2@test.ru",
                "+79990000002",
                "password-hash"
        );

        when(userRepository.findByUsername("user1"))
                .thenReturn(Optional.of(user1));

        when(userRepository.findByUsername("user2"))
                .thenReturn(Optional.of(user2));
    }

    @Test
    void shouldReturnUnauthorizedWhenProfileRequestedWithoutToken()
            throws Exception {

        mockMvc.perform(get("/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Authentication is required"));
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenIsInvalid()
            throws Exception {

        mockMvc.perform(get("/profile")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void shouldAllowUserToGetOwnProfile()
            throws Exception {

        String token = jwtService.generateToken(user1);

        when(profileService.getProfile(any()))
                .thenReturn(userDetails(user1));

        mockMvc.perform(get("/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("user1"));
    }

    @Test
    void shouldAllowUserToGetOwnUserById()
            throws Exception {

        String token = jwtService.generateToken(user1);

        when(userService.findById(1L))
                .thenReturn(userDetails(user1));

        mockMvc.perform(get("/users/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldForbidUserToGetAnotherUser() throws Exception {
        String token = jwtService.generateToken(user2);

        mockMvc.perform(get("/users/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void shouldForbidAccessToUserList() throws Exception {
        String token = jwtService.generateToken(user1);

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void shouldForbidUserToUpdateAnotherUser() throws Exception {
        String token = jwtService.generateToken(user2);

        mockMvc.perform(put("/users/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "user1",
                                  "firstName": "Changed",
                                  "lastName": "Ivanov",
                                  "email": "user1@test.ru",
                                  "phone": "+79990000001"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldForbidProfileUpdateWithoutAuthentication() throws Exception {
        mockMvc.perform(put("/profile")
                        .contentType("application/json")
                        .content("""
                            {
                              "firstName": "Changed",
                              "lastName": "Ivanov",
                              "email": "changed@test.ru",
                              "phone": "+79990000001"
                            }
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    private UserDetails userDetails(User user) {
        return new UserDetails(
                user.id(),
                user.username(),
                user.firstName(),
                user.lastName(),
                user.email(),
                user.phone()
        );
    }
}
