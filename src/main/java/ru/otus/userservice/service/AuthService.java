package ru.otus.userservice.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.userservice.dto.RegisterCommand;
import ru.otus.userservice.exception.InvalidCredentialsException;
import ru.otus.userservice.security.jwt.JwtService;
import ru.otus.userservice.dto.UserDetails;
import ru.otus.userservice.entity.User;
import ru.otus.userservice.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserDetails register(RegisterCommand command) {
        User user = new User(
                null,
                command.username(),
                command.firstName(),
                command.lastName(),
                command.email(),
                command.phone(),
                passwordEncoder.encode(command.password())
        );
        User savedUser = userRepository.save(user);

        return UserDetails.from(savedUser);
    }

    @Transactional(readOnly = true)
    public String login(String username, String password) {
        User user = findUserByUsername(username);

        if (!passwordMatches(password, user)) {
            throw new InvalidCredentialsException();
        }

        return jwtService.generateToken(user);
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);
    }

    private boolean passwordMatches(String rawPassword, User user) {
        return user.passwordHash() != null
                && passwordEncoder.matches(rawPassword, user.passwordHash());
    }
}
