package ru.otus.userservice.service;

import ru.otus.userservice.dto.CreateUserRequest;
import ru.otus.userservice.dto.UpdateUserRequest;
import ru.otus.userservice.dto.UserResponse;
import ru.otus.userservice.entity.User;
import ru.otus.userservice.exception.UserNotFoundException;
import ru.otus.userservice.mapper.UserMapper;
import ru.otus.userservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.StreamSupport;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        User user = UserMapper.fromCreateRequest(request);
        User savedUser = userRepository.save(user);

        return UserMapper.toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userRepository.findById(id)
                .map(UserMapper::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return StreamSupport.stream(userRepository.findAll().spliterator(), false)
                .map(UserMapper::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }

        User user = UserMapper.fromUpdateRequest(id, request);
        User savedUser = userRepository.save(user);

        return UserMapper.toResponse(savedUser);
    }

    @Transactional
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }

        userRepository.deleteById(id);
    }
}