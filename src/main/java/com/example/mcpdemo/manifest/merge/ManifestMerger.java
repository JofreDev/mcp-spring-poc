package com.example.mcpdemo.manifest.merge;

import com.example.mcpdemo.manifest.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ManifestMerger {

    public EffectiveManifest merge(GeneratedManifest generatedManifest, ManifestOverrides overrides) {
        Map<String, ToolOverride> toolOverrides = overrides != null && overrides.tools() != null ? overrides.tools() : Map.of();
        Map<String, ResourceOverride> resourceOverrides = overrides != null && overrides.resources() != null ? overrides.resources() : Map.of();

        List<ToolDescriptor> effectiveTools = new ArrayList<>();
        for (ToolDescriptor tool : generatedManifest.tools()) {
            ToolDescriptor merged = tool.withOverride(toolOverrides.get(tool.name()));
            if (!merged.excluded()) {
                effectiveTools.add(merged);
            }
        }

        List<ResourceDescriptor> effectiveResources = new ArrayList<>();
        for (ResourceDescriptor resource : generatedManifest.resources()) {
            ResourceDescriptor merged = resource.withOverride(resourceOverrides.get(resource.uri()));
            if (!merged.excluded()) {
                effectiveResources.add(merged);
            }
        }

        assert overrides != null;
        return new EffectiveManifest(effectiveTools, effectiveResources, overrides.prompts());
    }
}
