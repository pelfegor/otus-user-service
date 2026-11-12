package ru.otus.userservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.userservice.entity.User;
import ru.otus.userservice.exception.UserNotFoundException;
import ru.otus.userservice.repository.UserRepository;
import ru.otus.userservice.dto.UpdateUserCommand;
import ru.otus.userservice.dto.UserDetails;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserDetails findById(Long id) {
        return UserDetails.from(findUser(id));
    }

    @Transactional
    public UserDetails update(Long id, UpdateUserCommand command) {
        User user = findUser(id).withDetails(
                command.username(),
                command.firstName(),
                command.lastName(),
                command.email(),
                command.phone()
        );
        User savedUser = userRepository.save(user);

        log.atInfo()
                .addKeyValue("user_id", savedUser.id())
                .log("User updated");

        return UserDetails.from(savedUser);
    }

    @Transactional
    public void delete(Long id) {
        ensureUserExists(id);
        userRepository.deleteById(id);

        log.atInfo()
                .addKeyValue("user_id", id)
                .log("User deleted");
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private void ensureUserExists(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }
    }
}
