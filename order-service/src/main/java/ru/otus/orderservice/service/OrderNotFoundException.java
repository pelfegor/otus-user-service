package ru.otus.orderservice.service;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(long id) { super("Order not found: " + id); }
}
