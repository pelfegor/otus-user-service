package ru.otus.userservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.otus.userservice.dto.UpdateUserCommand;
import ru.otus.userservice.dto.UserDetails;
import ru.otus.userservice.entity.User;
import ru.otus.userservice.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldPreservePasswordHashWhenUpdatingUserDetails() {
        User existingUser = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserDetails response = userService.update(
                1L,
                new UpdateUserCommand(
                        "new-username",
                        "New name",
                        "New surname",
                        "new@example.com",
                        null
                )
        );

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().passwordHash())
                .isEqualTo("password-hash");
        assertThat(response.username()).isEqualTo("new-username");
    }

    private User user() {
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
}
