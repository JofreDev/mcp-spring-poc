package com.example.mcpdemo.manifest.model;

import java.util.List;

public record EffectiveManifest(
        List<ToolDescriptor> tools,
        List<ResourceDescriptor> resources,
        List<PromptDescriptor> prompts
) {
}
