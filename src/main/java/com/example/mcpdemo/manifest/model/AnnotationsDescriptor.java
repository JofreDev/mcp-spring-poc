package com.example.mcpdemo.manifest.model;

public record AnnotationsDescriptor(
        Boolean readOnlyHint,
        Boolean destructiveHint,
        Boolean idempotentHint,
        Boolean openWorldHint
) {

    public static AnnotationsDescriptor defaultsForHttpMethod(String method) {
        boolean readOnly = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
        return new AnnotationsDescriptor(readOnly, false, readOnly, false);
    }

    public AnnotationsDescriptor merge(AnnotationsDescriptor override) {
        if (override == null) {
            return this;
        }
        return new AnnotationsDescriptor(
                override.readOnlyHint != null ? override.readOnlyHint : this.readOnlyHint,
                override.destructiveHint != null ? override.destructiveHint : this.destructiveHint,
                override.idempotentHint != null ? override.idempotentHint : this.idempotentHint,
                override.openWorldHint != null ? override.openWorldHint : this.openWorldHint
        );
    }
}
