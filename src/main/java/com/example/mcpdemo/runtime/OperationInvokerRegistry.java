package com.example.mcpdemo.runtime;

import java.util.Optional;

public interface OperationInvokerRegistry {

    OperationInvoker requiredToolExecution(String toolName);

    default Optional<ResourceInvoker> findResourceExecution(String resourceUri) {
        return Optional.empty();
    }

    default ResourceInvoker requiredResourceExecution(String resourceUri) {
        return findResourceExecution(resourceUri)
                .orElseThrow(() -> new IllegalArgumentException("No resource execution registered for: " + resourceUri));
    }
}
