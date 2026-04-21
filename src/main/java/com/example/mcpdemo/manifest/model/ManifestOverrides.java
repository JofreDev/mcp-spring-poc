package com.example.mcpdemo.manifest.model;

import java.util.List;
import java.util.Map;

public record ManifestOverrides(
        Map<String, ToolOverride> tools,
        Map<String, ResourceOverride> resources,/*,
        Map<String, PromptDescriptor> prompts*/
        List<PromptDescriptor> prompts
) {
}
