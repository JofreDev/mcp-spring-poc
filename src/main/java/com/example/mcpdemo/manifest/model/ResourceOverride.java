package com.example.mcpdemo.manifest.model;

public record ResourceOverride(
        String title,
        String description,
        String mimeType,
        String text,
        Boolean exclude
) {
}
