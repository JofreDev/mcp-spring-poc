package com.example.mcpdemo.sample.usecase;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CustomerStatusResourceUseCase {

    private final MockCustomerStatusClient mockCustomerStatusClient;

    public CustomerStatusResourceUseCase(MockCustomerStatusClient mockCustomerStatusClient) {
        this.mockCustomerStatusClient = mockCustomerStatusClient;
    }

    public ResourceSummaryResponse get(String resourceUri) {
        Map<String, Object> mockedGetPayload = mockCustomerStatusClient.get();
        return response(resourceUri, "Resource summary resolved from use case", mockedGetPayload);
    }

    public ResourceSummaryResponse get(String resourceUri, String customerDocument) {
        Map<String, Object> mockedGetPayload = mockCustomerStatusClient.get(customerDocument);
        return response(resourceUri, "Resource summary resolved from use case with one argument", mockedGetPayload);
    }

    public ResourceSummaryResponse get(String resourceUri, String region, String status) {
        Map<String, Object> mockedGetPayload = mockCustomerStatusClient.get(region, status);
        return response(resourceUri, "Resource summary resolved from use case with multiple arguments", mockedGetPayload);
    }

    private ResourceSummaryResponse response(String resourceUri, String message, Map<String, Object> mockedGetPayload) {
        return new ResourceSummaryResponse(
                resourceUri,
                message,
                mockedGetPayload
        );
    }

    public record ResourceSummaryResponse(
            String resourceUri,
            String message,
            Map<String, Object> externalGetPayload
    ) {
    }
}
