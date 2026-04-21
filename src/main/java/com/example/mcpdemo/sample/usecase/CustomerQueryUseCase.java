package com.example.mcpdemo.sample.usecase;

import org.springframework.stereotype.Service;

@Service
public class CustomerQueryUseCase {

    public CustomerResponse getById(String customerId) {
        return new CustomerResponse(customerId, "Ana Gómez", "ana@example.com", "ACTIVE");
    }

    public record CustomerResponse(String id, String fullName, String email, String status) {}
}
