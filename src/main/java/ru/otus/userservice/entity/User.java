package ru.otus.userservice.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
public record User(
        @Id
        Long id,
        String username,
        String firstName,
        String lastName,
        String email,
        String phone,
        @Column("password")
        String passwordHash
) {

    public User withDetails(
            String username,
            String firstName,
            String lastName,
            String email,
            String phone
    ) {
        return new User(
                id,
                username,
                firstName,
                lastName,
                email,
                phone,
                passwordHash
        );
    }

    public User withProfile(
            String firstName,
            String lastName,
            String email,
            String phone
    ) {
        return withDetails(username, firstName, lastName, email, phone);
    }
}
