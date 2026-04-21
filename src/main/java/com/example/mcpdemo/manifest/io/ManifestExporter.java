package com.example.mcpdemo.manifest.io;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ManifestExporter {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public void export(Path baseDir, String fileName, Object body) {
        try {
            Files.createDirectories(baseDir);
            yamlMapper.writerWithDefaultPrettyPrinter().writeValue(baseDir.resolve(fileName).toFile(), body);
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to export manifest " + fileName, e);
        }
    }
}
