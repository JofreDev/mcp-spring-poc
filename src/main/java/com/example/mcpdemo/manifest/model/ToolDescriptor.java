package com.example.mcpdemo.manifest.model;

import java.util.Map;

public record ToolDescriptor(
        String sourceOperationId,
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        AnnotationsDescriptor annotations,
        BindingDescriptor binding,
        boolean excluded
) {

    public ToolDescriptor withOverride(ToolOverride override) {
        if (override == null) {
            return this;
        }
        return new ToolDescriptor(
                sourceOperationId,
                override.name() != null ? override.name() : name,
                override.title() != null ? override.title() : title,
                override.description() != null ? override.description() : description,
                inputSchema,
                outputSchema,
                annotations != null ? annotations.merge(override.annotations()) : override.annotations(),
                override.binding() != null ? override.binding() : binding,
                override.exclude() != null ? override.exclude() : excluded
        );
    }
}
