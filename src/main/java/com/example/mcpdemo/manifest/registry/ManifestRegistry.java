package com.example.mcpdemo.manifest.registry;

import com.example.mcpdemo.manifest.model.EffectiveManifest;
import com.example.mcpdemo.manifest.model.PromptDescriptor;
import com.example.mcpdemo.manifest.model.ResourceDescriptor;
import com.example.mcpdemo.manifest.model.ToolDescriptor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ManifestRegistry {

    private final Map<String, ToolDescriptor> toolsByName = new ConcurrentHashMap<>();
    private final Map<String, ResourceDescriptor> resourcesByUri = new ConcurrentHashMap<>();
    private final Map<String, PromptDescriptor> promptsByName = new ConcurrentHashMap<>();

    public ManifestRegistry(EffectiveManifest effectiveManifest) {
        effectiveManifest.tools().forEach(tool -> toolsByName.put(tool.name(), tool));
        effectiveManifest.resources().forEach(resource -> resourcesByUri.put(resource.uri(), resource));
        effectiveManifest.prompts().forEach(prompt -> promptsByName.put(prompt.name(), prompt));
    }

    public Collection<ToolDescriptor> allTools() {
        return toolsByName.values();
    }

    public Collection<ResourceDescriptor> allResources() {
        return resourcesByUri.values();
    }

    public Collection<PromptDescriptor> allPrompts() { return promptsByName.values();}

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
}
