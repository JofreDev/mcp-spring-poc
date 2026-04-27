package com.example.mcpdemo.manifest.io;

import com.example.mcpdemo.manifest.model.ManifestOverrides;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Component
public class ManifestYamlLoader {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public ManifestYamlLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public ManifestOverrides loadOverrides(String location) {
        return loadOptional(location, ManifestOverrides.class)
                .orElseThrow(() -> new IllegalStateException("Unable to read overrides from " + location));
    }

    public <T> Optional<T> loadOptional(String location, Class<T> bodyType) {
        if (location == null || location.isBlank()) {
            return Optional.empty();
        }

        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return Optional.empty();
        }

        try (InputStream is = resource.getInputStream()) {
            return Optional.of(yamlMapper.readValue(is, bodyType));
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to read YAML from " + location, e);
        }
    }
}
