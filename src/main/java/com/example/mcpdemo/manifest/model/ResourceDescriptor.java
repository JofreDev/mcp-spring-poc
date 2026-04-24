package com.example.mcpdemo.manifest.model;

public record ResourceDescriptor(
        String uri,
        String title,
        String description,
        String mimeType,
        String text,
        String uriTemplate,
        BindingDescriptor binding,
        boolean excluded
) {

    public ResourceDescriptor withOverride(ResourceOverride override) {
        if (override == null) {
            return this;
        }
        return new ResourceDescriptor(
                uri,
                override.title() != null ? override.title() : title,
                override.description() != null ? override.description() : description,
                override.mimeType() != null ? override.mimeType() : mimeType,
                override.text() != null ? override.text() : text,
                override.uriTemplate() != null ? override.uriTemplate() : uriTemplate,
                binding != null ? binding.merge(override.binding()) : override.binding(),
                override.exclude() != null ? override.exclude() : excluded
        );
    }
}
