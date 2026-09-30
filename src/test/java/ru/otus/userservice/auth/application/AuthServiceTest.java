package ru.otus.userservice.auth.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.otus.userservice.dto.RegisterCommand;
import ru.otus.userservice.exception.InvalidCredentialsException;
import ru.otus.userservice.security.jwt.JwtService;
import ru.otus.userservice.service.AuthService;
import ru.otus.userservice.dto.UserDetails;
import ru.otus.userservice.entity.User;
import ru.otus.userservice.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldEncodePasswordWhenRegisteringUser() {
        RegisterCommand command = registrationCommand();
        when(passwordEncoder.encode("plain-password")).thenReturn("password-hash");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0)));

        UserDetails response = authService.register(command);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().passwordHash())
                .isEqualTo("password-hash");
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("alice");
    }

    @Test
    void shouldReturnTokenForValidCredentials() {
        User user = registeredUser();
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "password-hash"))
                .thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("signed-token");

        String token = authService.login("alice", "plain-password");

        assertThat(token).isEqualTo("signed-token");
    }

    @Test
    void shouldRejectInvalidCredentialsWithoutCreatingToken() {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(registeredUser()));
        when(passwordEncoder.matches("wrong-password", "password-hash"))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(jwtService);
    }

    private RegisterCommand registrationCommand() {
        return new RegisterCommand(
                "alice",
                "plain-password",
                "Alice",
                "Smith",
                "alice@example.com",
                "+79990000000"
        );
    }

    private User registeredUser() {
        return new User(
                1L,
                "alice",
                "Alice",
                "Smith",
                "alice@example.com",
                "+79990000000",
                "password-hash"
        );
    }

    private User withId(User user) {
        return new User(
                1L,
                user.username(),
                user.firstName(),
                user.lastName(),
                user.email(),
                user.phone(),
                user.passwordHash()
        );
    }
}
