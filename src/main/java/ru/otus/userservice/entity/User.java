package ru.otus.userservice.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
public record User(
        @Id
        Long id,
        String username,
        String firstName,
        String lastName,
        String email,
        String phone
) {
}