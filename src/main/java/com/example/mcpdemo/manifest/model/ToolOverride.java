package com.example.mcpdemo.manifest.model;

public record ToolOverride(
        String name,
        String title,
        String description,
        AnnotationsDescriptor annotations,
        BindingDescriptor binding,
        Boolean exclude
) {
}
