package ru.otus.userservice.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.userservice.dto.UpdateProfileRequest;
import ru.otus.userservice.service.ProfileService;
import ru.otus.userservice.security.dto.AuthenticatedUser;
import ru.otus.userservice.dto.UserResponse;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public UserResponse getProfile(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return UserResponse.from(profileService.getProfile(user.id()));
    }

    @PutMapping
    public UserResponse updateProfile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return UserResponse.from(
                profileService.updateProfile(user.id(), request.toCommand())
        );
    }
}
