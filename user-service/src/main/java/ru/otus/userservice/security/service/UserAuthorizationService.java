package ru.otus.userservice.security.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import ru.otus.userservice.security.dto.AuthenticatedUser;

@Service("userAuthorization")
public class UserAuthorizationService {

    public boolean isOwner(Long userId, Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return false;
        }

        return user.id().equals(userId);
    }
}
