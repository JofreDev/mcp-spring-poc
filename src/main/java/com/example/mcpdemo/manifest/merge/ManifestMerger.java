package com.example.mcpdemo.manifest.merge;

import com.example.mcpdemo.manifest.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ManifestMerger {

    public EffectiveManifest merge(GeneratedManifest generatedManifest, ManifestOverrides overrides) {
        Objects.requireNonNull(generatedManifest, "generatedManifest cannot be null");

        Map<String, ToolOverride> toolOverrides = overrides != null && overrides.tools() != null ? overrides.tools() : Map.of();
        Map<String, ResourceOverride> resourceOverrides = overrides != null && overrides.resources() != null ? overrides.resources() : Map.of();
        List<PromptDescriptor> promptOverrides = overrides != null && overrides.prompts() != null ? overrides.prompts() : List.of();

        List<ToolDescriptor> generatedTools = generatedManifest.tools() != null ? generatedManifest.tools() : List.of();
        List<ResourceDescriptor> generatedResources = generatedManifest.resources() != null ? generatedManifest.resources() : List.of();

        List<ToolDescriptor> effectiveTools = new ArrayList<>();
        for (ToolDescriptor tool : generatedTools) {
            if (tool == null) {
                continue;
            }
            ToolDescriptor merged = tool.withOverride(toolOverrides.get(tool.name()));
            if (!merged.excluded()) {
                effectiveTools.add(merged);
            }
        }

        List<ResourceDescriptor> effectiveResources = new ArrayList<>();
        for (ResourceDescriptor resource : generatedResources) {
            if (resource == null) {
                continue;
            }
            ResourceDescriptor merged = resource.withOverride(resourceOverrides.get(resource.uri()));
            if (!merged.excluded()) {
                effectiveResources.add(merged);
            }
        }

        List<PromptDescriptor> effectivePrompts = promptOverrides.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(prompt -> prompt.name() != null ? prompt.name() : ""))
                .toList();

        List<ToolDescriptor> orderedTools = effectiveTools.stream()
                .sorted(Comparator.comparing(tool -> tool.name() != null ? tool.name() : ""))
                .toList();

        List<ResourceDescriptor> orderedResources = effectiveResources.stream()
                .sorted(Comparator.comparing(resource -> resource.uri() != null ? resource.uri() : ""))
                .toList();

        return new EffectiveManifest(orderedTools, orderedResources, effectivePrompts);
    }
}
