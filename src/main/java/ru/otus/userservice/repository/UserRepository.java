package ru.otus.userservice.repository;

import org.springframework.data.repository.CrudRepository;
import ru.otus.userservice.entity.User;

import java.util.Optional;

public interface UserRepository extends CrudRepository<User, Long> {

    Optional<User> findByUsername(String username);
}
