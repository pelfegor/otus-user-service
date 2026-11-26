package ru.otus.deliveryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"ru.otus.deliveryservice", "ru.otus.common.security"})
public class DeliveryServiceApplication {
    public static void main(String[] args) { SpringApplication.run(DeliveryServiceApplication.class, args); }
}
