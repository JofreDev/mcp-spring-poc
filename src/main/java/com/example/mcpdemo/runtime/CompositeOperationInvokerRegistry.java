package com.example.mcpdemo.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CompositeOperationInvokerRegistry implements OperationInvokerRegistry, OperationExecutionRegistrar {

    private final Map<String, OperationInvoker> toolExecutions = new ConcurrentHashMap<>();
    private final Map<String, ResourceInvoker> resourceExecutions = new ConcurrentHashMap<>();

    public CompositeOperationInvokerRegistry(List<OperationExecutionContributor> contributors) {
        Objects.requireNonNull(contributors, "contributors cannot be null");
        contributors.forEach(contributor -> contributor.contribute(this));
    }

    @Override
    public void registerToolExecution(String toolName, OperationInvoker execution) {
        String key = requireIdentifier("toolName", toolName);
        OperationInvoker current = toolExecutions.putIfAbsent(key, Objects.requireNonNull(execution, "execution cannot be null"));
        if (current != null) {
            throw new IllegalStateException("A tool execution is already registered for: " + key);
        }
    }

    @Override
    public void registerResourceExecution(String resourceUri, ResourceInvoker execution) {
        String key = requireIdentifier("resourceUri", resourceUri);
        ResourceInvoker current = resourceExecutions.putIfAbsent(key, Objects.requireNonNull(execution, "execution cannot be null"));
        if (current != null) {
            throw new IllegalStateException("A resource execution is already registered for: " + key);
        }
    }

    @Override
    public OperationInvoker requiredToolExecution(String toolName) {
        String key = requireIdentifier("toolName", toolName);
        OperationInvoker execution = toolExecutions.get(key);
        if (execution == null) {
            throw new IllegalArgumentException("No tool execution registered for: " + key);
        }
        return execution;
    }

    @Override
    public Optional<ResourceInvoker> findResourceExecution(String resourceUri) {
        return Optional.ofNullable(resourceExecutions.get(requireIdentifier("resourceUri", resourceUri)));
    }

    private String requireIdentifier(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
        return value;
    }
}
