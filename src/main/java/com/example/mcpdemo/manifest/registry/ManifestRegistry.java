package com.example.mcpdemo.manifest.registry;

import com.example.mcpdemo.manifest.model.EffectiveManifest;
import com.example.mcpdemo.manifest.model.PromptDescriptor;
import com.example.mcpdemo.manifest.model.ResourceDescriptor;
import com.example.mcpdemo.manifest.model.ToolDescriptor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

@Component
public class ManifestRegistry {

    private final NavigableMap<String, ToolDescriptor> toolsByName = new TreeMap<>();
    private final NavigableMap<String, ResourceDescriptor> resourcesByUri = new TreeMap<>();
    private final NavigableMap<String, PromptDescriptor> promptsByName = new TreeMap<>();

    public ManifestRegistry(EffectiveManifest effectiveManifest) {
        Objects.requireNonNull(effectiveManifest, "effectiveManifest cannot be null");

        registerTools(effectiveManifest.tools());
        registerResources(effectiveManifest.resources());
        registerPrompts(effectiveManifest.prompts());
    }

    public Collection<ToolDescriptor> allTools() {
        return List.copyOf(toolsByName.values());
    }

    public Collection<ResourceDescriptor> allResources() {
        return List.copyOf(resourcesByUri.values());
    }

    public Collection<PromptDescriptor> allPrompts() {
        return List.copyOf(promptsByName.values());
    }

    public ToolDescriptor requiredTool(String name) {
        ToolDescriptor descriptor = toolsByName.get(name);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return descriptor;
    }

    public ResourceDescriptor resource(String uri) {
        return resourcesByUri.get(uri);
    }

    private void registerTools(List<ToolDescriptor> tools) {
        if (tools == null) {
            return;
        }
        for (ToolDescriptor tool : tools) {
            if (tool == null) {
                continue;
            }

            String toolName = requireIdentifier("tool.name", tool.name());
            ToolDescriptor previous = toolsByName.putIfAbsent(toolName, tool);
            if (previous != null) {
                throw new IllegalStateException("Duplicate tool name in effective manifest: " + toolName);
            }
        }
    }

    private void registerResources(List<ResourceDescriptor> resources) {
        if (resources == null) {
            return;
        }
        for (ResourceDescriptor resource : resources) {
            if (resource == null) {
                continue;
            }

            String resourceUri = requireIdentifier("resource.uri", resource.uri());
            ResourceDescriptor previous = resourcesByUri.putIfAbsent(resourceUri, resource);
            if (previous != null) {
                throw new IllegalStateException("Duplicate resource uri in effective manifest: " + resourceUri);
            }
        }
    }

    private void registerPrompts(List<PromptDescriptor> prompts) {
        if (prompts == null) {
            return;
        }
        for (PromptDescriptor prompt : prompts) {
            if (prompt == null) {
                continue;
            }

            String promptName = requireIdentifier("prompt.name", prompt.name());
            PromptDescriptor previous = promptsByName.putIfAbsent(promptName, prompt);
            if (previous != null) {
                throw new IllegalStateException("Duplicate prompt name in effective manifest: " + promptName);
            }
        }
    }

    private String requireIdentifier(String field, String value) {
        String identifier = Objects.requireNonNull(value, field + " cannot be null");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return identifier;
    }
}
