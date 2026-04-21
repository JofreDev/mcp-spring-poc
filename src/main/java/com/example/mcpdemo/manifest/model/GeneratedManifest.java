package com.example.mcpdemo.manifest.model;

import java.util.List;

public record GeneratedManifest(
        List<ToolDescriptor> tools,
        List<ResourceDescriptor> resources
) {
}
