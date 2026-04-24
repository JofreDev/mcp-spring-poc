package com.example.mcpdemo.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractOperationExecutionContributor implements OperationExecutionContributor {

    private final ObjectMapper objectMapper;
    private OperationExecutionRegistrar registrar;

    protected AbstractOperationExecutionContributor() {
        this(new ObjectMapper());
    }

    protected AbstractOperationExecutionContributor(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    @Override
    public final void contribute(OperationExecutionRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar cannot be null");
        try {
            registerExecutions();
        }
        finally {
            this.registrar = null;
        }
    }

    protected abstract void registerExecutions();

    protected final void registerToolExecution(String toolName, OperationInvoker execution) {
        currentRegistrar().registerToolExecution(toolName, execution);
    }

    protected final void registerResourceExecution(String resourceUri, ResourceInvoker execution) {
        currentRegistrar().registerResourceExecution(resourceUri, execution);
    }

    protected final <T> T toolArgAs(Map<String, Object> args, String argName, Class<T> targetType) {
        return objectMapper.convertValue(requiredToolArg(args, argName), Objects.requireNonNull(targetType, "targetType cannot be null"));
    }

    protected final <T> T toolArgAs(Map<String, Object> args, String argName, TypeReference<T> targetType) {
        return objectMapper.convertValue(requiredToolArg(args, argName), Objects.requireNonNull(targetType, "targetType cannot be null"));
    }

    protected final <T> T optionalToolArgAs(Map<String, Object> args, String argName, Class<T> targetType) {
        Object value = optionalToolArg(args, argName);
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, Objects.requireNonNull(targetType, "targetType cannot be null"));
    }

    protected final <T> T optionalToolArgAs(Map<String, Object> args, String argName, TypeReference<T> targetType) {
        Object value = optionalToolArg(args, argName);
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, Objects.requireNonNull(targetType, "targetType cannot be null"));
    }

    protected final JsonNode toolArgAsJson(Map<String, Object> args, String argName) {
        return objectMapper.valueToTree(requiredToolArg(args, argName));
    }

    protected final JsonNode optionalToolArgAsJson(Map<String, Object> args, String argName) {
        Object value = optionalToolArg(args, argName);
        return value == null ? null : objectMapper.valueToTree(value);
    }

    protected final <T> T toolBodyAs(Map<String, Object> args, Class<T> targetType) {
        return toolArgAs(args, "body", targetType);
    }

    protected final <T> T toolBodyAs(Map<String, Object> args, TypeReference<T> targetType) {
        return toolArgAs(args, "body", targetType);
    }

    protected final JsonNode toolBodyAsJson(Map<String, Object> args) {
        return toolArgAsJson(args, "body");
    }

    protected final <T> T optionalToolBodyAs(Map<String, Object> args, Class<T> targetType) {
        return optionalToolArgAs(args, "body", targetType);
    }

    protected final <T> T optionalToolBodyAs(Map<String, Object> args, TypeReference<T> targetType) {
        return optionalToolArgAs(args, "body", targetType);
    }

    protected final JsonNode optionalToolBodyAsJson(Map<String, Object> args) {
        return optionalToolArgAsJson(args, "body");
    }

    protected final String requiredResourceQueryParam(String resourceUri, String paramName) {
        String value = optionalResourceQueryParam(resourceUri, paramName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required resource query parameter: " + requireArgName(paramName));
        }
        return value;
    }

    protected final String optionalResourceQueryParam(String resourceUri, String paramName) {
        return resourceQueryParams(resourceUri).get(requireArgName(paramName));
    }

    protected final Map<String, String> resourceQueryParams(String resourceUri) {
        String rawUri = Objects.requireNonNull(resourceUri, "resourceUri cannot be null");
        String rawQuery = URI.create(rawUri).getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }

        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            if (pair == null || pair.isBlank()) {
                continue;
            }

            int delimiterIndex = pair.indexOf('=');
            String rawKey = delimiterIndex >= 0 ? pair.substring(0, delimiterIndex) : pair;
            String rawValue = delimiterIndex >= 0 ? pair.substring(delimiterIndex + 1) : "";

            String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
            String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
            params.putIfAbsent(key, value);
        }
        return params;
    }

    protected final Object requiredToolArg(Map<String, Object> args, String argName) {
        String key = requireArgName(argName);
        Objects.requireNonNull(args, "args cannot be null");
        if (!args.containsKey(key) || args.get(key) == null) {
            throw new IllegalArgumentException("Missing required tool argument: " + key);
        }
        return args.get(key);
    }

    protected final Object optionalToolArg(Map<String, Object> args, String argName) {
        Objects.requireNonNull(args, "args cannot be null");
        return args.get(requireArgName(argName));
    }

    private String requireArgName(String argName) {
        String key = Objects.requireNonNull(argName, "argName cannot be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("argName cannot be blank");
        }
        return key;
    }

    private OperationExecutionRegistrar currentRegistrar() {
        if (registrar == null) {
            throw new IllegalStateException("Operation executions can only be registered during contribution");
        }
        return registrar;
    }
}
