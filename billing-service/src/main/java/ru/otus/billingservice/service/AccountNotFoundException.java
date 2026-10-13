package ru.otus.billingservice.service;
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(long userId) {
        super("Billing account not found for userId=" + userId);
    }
}
