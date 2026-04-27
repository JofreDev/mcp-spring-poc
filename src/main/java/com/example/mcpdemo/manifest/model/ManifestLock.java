package com.example.mcpdemo.manifest.model;

public record ManifestLock(
        int schemaVersion,
        String algorithm,
        String generatedManifestSha256,
        String effectiveManifestSha256,
        int generatedTools,
        int generatedResources,
        int effectiveTools,
        int effectiveResources,
        int effectivePrompts
) {
}
