package ru.otus.userservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.userservice.dto.UpdateProfileCommand;
import ru.otus.userservice.dto.UserDetails;
import ru.otus.userservice.exception.UserNotFoundException;
import ru.otus.userservice.entity.User;
import ru.otus.userservice.repository.UserRepository;

@Service
public class ProfileService {

    private static final Logger log =
            LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserDetails getProfile(Long userId) {
        return UserDetails.from(findUser(userId));
    }

    @Transactional
    public UserDetails updateProfile(
            Long userId,
            UpdateProfileCommand command
    ) {
        User updatedUser = findUser(userId).withProfile(
                command.firstName(),
                command.lastName(),
                command.email(),
                command.phone()
        );
        User savedUser = userRepository.save(updatedUser);

        log.atInfo()
                .addKeyValue("user_id", savedUser.id())
                .log("User profile updated");

        return UserDetails.from(savedUser);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
