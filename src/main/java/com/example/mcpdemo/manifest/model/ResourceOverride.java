package com.example.mcpdemo.manifest.model;

public record ResourceOverride(
        String title,
        String description,
        String mimeType,
        String text,
        String uriTemplate,
        BindingDescriptor binding,
        Boolean exclude
) {
}
