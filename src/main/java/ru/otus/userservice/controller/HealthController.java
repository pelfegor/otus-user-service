package ru.otus.userservice.controller;

import ru.otus.userservice.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health/")
    public HealthResponse health() {
        return HealthResponse.ok();
    }
}