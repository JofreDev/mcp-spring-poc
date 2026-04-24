package com.example.mcpdemo.manifest.io;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ManifestExporter {

    private final ObjectMapper yamlMapper;

    public ManifestExporter() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public void export(Path baseDir, String fileName, Object body) {
        Path output = baseDir.resolve(fileName);
        try {
            Files.createDirectories(baseDir);
            yamlMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), body);
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to export manifest " + fileName, e);
        }
    }
}
