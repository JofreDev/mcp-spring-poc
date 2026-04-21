package com.example.mcpdemo.sample.usecase;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderQueryUseCase {

    public OrderSearchResponse search(String customerDocument, String status) {
        return new OrderSearchResponse(
                List.of(new OrderItem("O-100", status != null ? status : "PAID", 120.50, customerDocument)),
                1
        );
    }

    public record OrderSearchResponse(List<OrderItem> items, int total) {}
    public record OrderItem(String orderId, String status, double total, String customerDocument) {}
}
