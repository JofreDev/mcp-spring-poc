package com.example.mcpdemo.manifest.io;

import com.example.mcpdemo.manifest.model.ManifestOverrides;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ManifestYamlLoader {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public ManifestYamlLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public ManifestOverrides loadOverrides(String location) {
        try (InputStream is = resourceLoader.getResource(location).getInputStream()) {
            return yamlMapper.readValue(is, ManifestOverrides.class);
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to read overrides from " + location, e);
        }
    }
}
