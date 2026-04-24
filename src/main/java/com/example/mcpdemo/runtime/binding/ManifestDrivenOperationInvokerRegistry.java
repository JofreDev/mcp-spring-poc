package com.example.mcpdemo.runtime.binding;

import com.example.mcpdemo.manifest.model.BindingDescriptor;
import com.example.mcpdemo.manifest.model.ResourceDescriptor;
import com.example.mcpdemo.manifest.model.ToolDescriptor;
import com.example.mcpdemo.manifest.registry.ManifestRegistry;
import com.example.mcpdemo.runtime.OperationInvoker;
import com.example.mcpdemo.runtime.OperationInvokerRegistry;
import com.example.mcpdemo.runtime.ResourceInvoker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ManifestDrivenOperationInvokerRegistry implements OperationInvokerRegistry {

    private final OperationInvokerRegistry contributorRegistry;
    private final Map<String, OperationInvoker> declarativeToolInvokers;
    private final Map<String, ResourceInvoker> declarativeResourceInvokers;

    public ManifestDrivenOperationInvokerRegistry(ManifestRegistry manifestRegistry,
                                                  OperationInvokerRegistry contributorRegistry,
                                                  List<BindingModeInvokerFactory> invokerFactories) {
        Objects.requireNonNull(manifestRegistry, "manifestRegistry cannot be null");
        this.contributorRegistry = Objects.requireNonNull(contributorRegistry, "contributorRegistry cannot be null");

        Map<String, BindingModeInvokerFactory> factoriesByMode = indexFactories(invokerFactories);
        this.declarativeToolInvokers = buildToolInvokers(manifestRegistry, factoriesByMode);
        this.declarativeResourceInvokers = buildResourceInvokers(manifestRegistry, factoriesByMode);
    }

    @Override
    public OperationInvoker requiredToolExecution(String toolName) {
        String key = requireIdentifier("toolName", toolName);
        OperationInvoker declarative = declarativeToolInvokers.get(key);
        if (declarative != null) {
            return declarative;
        }
        return contributorRegistry.requiredToolExecution(key);
    }

    @Override
    public Optional<ResourceInvoker> findResourceExecution(String resourceUri) {
        String key = requireIdentifier("resourceUri", resourceUri);
        ResourceInvoker declarative = declarativeResourceInvokers.get(key);
        if (declarative != null) {
            return Optional.of(declarative);
        }
        return contributorRegistry.findResourceExecution(key);
    }

    private Map<String, BindingModeInvokerFactory> indexFactories(List<BindingModeInvokerFactory> invokerFactories) {
        Map<String, BindingModeInvokerFactory> indexed = new LinkedHashMap<>();
        if (invokerFactories == null) {
            return Map.of();
        }

        for (BindingModeInvokerFactory factory : invokerFactories) {
            if (factory == null) {
                continue;
            }

            String mode = requireIdentifier("bindingMode", factory.mode()).toLowerCase(Locale.ROOT);
            BindingModeInvokerFactory previous = indexed.putIfAbsent(mode, factory);
            if (previous != null) {
                throw new IllegalStateException("Duplicate binding mode factory for mode '" + mode + "'");
            }
        }
        return Map.copyOf(indexed);
    }

    private Map<String, OperationInvoker> buildToolInvokers(ManifestRegistry manifestRegistry,
                                                             Map<String, BindingModeInvokerFactory> factoriesByMode) {
        Map<String, OperationInvoker> invokers = new LinkedHashMap<>();
        for (ToolDescriptor tool : manifestRegistry.allTools()) {
            if (tool == null || tool.excluded()) {
                continue;
            }

            BindingDescriptor binding = tool.binding();
            if (!hasDeclarativeBinding(binding)) {
                continue;
            }

            String mode = binding.effectiveMode();
            BindingModeInvokerFactory factory = requireFactory(mode, factoriesByMode, "tool '" + tool.name() + "'");
            OperationInvoker invoker = factory.createToolInvoker(tool)
                    .orElseThrow(() -> new IllegalStateException(
                            "Binding mode '" + mode + "' cannot create tool invoker for '" + tool.name() + "'"
                    ));

            String key = resolveToolKey(tool, binding);
            OperationInvoker previous = invokers.putIfAbsent(key, invoker);
            if (previous != null) {
                throw new IllegalStateException("Duplicate declarative tool execution for key '" + key + "'");
            }
        }
        return Map.copyOf(invokers);
    }

    private Map<String, ResourceInvoker> buildResourceInvokers(ManifestRegistry manifestRegistry,
                                                                Map<String, BindingModeInvokerFactory> factoriesByMode) {
        Map<String, ResourceInvoker> invokers = new LinkedHashMap<>();
        for (ResourceDescriptor resource : manifestRegistry.allResources()) {
            if (resource == null || resource.excluded()) {
                continue;
            }

            BindingDescriptor binding = resource.binding();
            if (!hasDeclarativeBinding(binding)) {
                continue;
            }

            String mode = binding.effectiveMode();
            BindingModeInvokerFactory factory = requireFactory(mode, factoriesByMode, "resource '" + resource.uri() + "'");
            ResourceInvoker invoker = factory.createResourceInvoker(resource)
                    .orElseThrow(() -> new IllegalStateException(
                            "Binding mode '" + mode + "' cannot create resource invoker for '" + resource.uri() + "'"
                    ));

            String key = requireIdentifier("resource.uri", resource.uri());
            ResourceInvoker previous = invokers.putIfAbsent(key, invoker);
            if (previous != null) {
                throw new IllegalStateException("Duplicate declarative resource execution for key '" + key + "'");
            }
        }
        return Map.copyOf(invokers);
    }

    private boolean hasDeclarativeBinding(BindingDescriptor binding) {
        return binding != null && !BindingDescriptor.MODE_REGISTRY.equals(binding.effectiveMode());
    }

    private BindingModeInvokerFactory requireFactory(String mode,
                                                     Map<String, BindingModeInvokerFactory> factoriesByMode,
                                                     String context) {
        BindingModeInvokerFactory factory = factoriesByMode.get(mode);
        if (factory == null) {
            throw new IllegalStateException("Unsupported binding mode '" + mode + "' for " + context);
        }
        return factory;
    }

    private String resolveToolKey(ToolDescriptor tool, BindingDescriptor binding) {
        String handler = binding.handler();
        if (handler != null && !handler.isBlank()) {
            return handler;
        }
        return requireIdentifier("tool.name", tool.name());
    }

    private String requireIdentifier(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " cannot be null or blank");
        }
        return value;
    }
}
