package ru.otus.userservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.userservice.dto.HealthResponse;

@RestController
public class HealthController {

    @GetMapping("/health/")
    public HealthResponse health() {
        return HealthResponse.ok();
    }
}
