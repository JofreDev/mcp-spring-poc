package com.example.mcpdemo.sample.usecase;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MockCustomerStatusClient {

    public Map<String, Object> get() {
        return buildPayload("DOC-100", "UP", "none");
    }

    public Map<String, Object> get(String customerDocument) {
        return buildPayload(customerDocument, "UP", "customerDocument");
    }

    public Map<String, Object> get(String region, String status) {
        return buildPayload(region, status, "region+status");
    }

    private Map<String, Object> buildPayload(String principalFilter, String status, String filterMode) {
        return Map.of(
                "source", "mock-customer-status-client",
                "status", status,
                "filterValue", principalFilter,
                "filterMode", filterMode,
                "activeChecks", List.of("customers", "orders", "risk-evaluation")
        );
    }
}
