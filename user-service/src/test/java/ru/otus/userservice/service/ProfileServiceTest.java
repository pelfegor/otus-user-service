package ru.otus.userservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.otus.userservice.dto.UpdateProfileCommand;
import ru.otus.userservice.entity.User;
import ru.otus.userservice.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void shouldPreserveUsernameAndPasswordWhenUpdatingProfile() {
        User existingUser = new User(
                1L,
                "alice",
                "Alice",
                "Smith",
                "alice@example.com",
                "+79990000000",
                "password-hash"
        );

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(existingUser));

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        profileService.updateProfile(
                1L,
                new UpdateProfileCommand(
                        "New Alice",
                        "New Smith",
                        "new@example.com",
                        "+79991111111"
                )
        );

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(captor.capture());

        User savedUser = captor.getValue();

        assertThat(savedUser.id()).isEqualTo(1L);
        assertThat(savedUser.username()).isEqualTo("alice");
        assertThat(savedUser.passwordHash()).isEqualTo("password-hash");

        assertThat(savedUser.firstName()).isEqualTo("New Alice");
        assertThat(savedUser.email()).isEqualTo("new@example.com");
    }
}
