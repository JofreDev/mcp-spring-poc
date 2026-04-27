package com.example.mcpdemo.manifest.io;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class ManifestExporter {

    private final ObjectMapper yamlMapper;
    private final ObjectMapper canonicalJsonMapper;

    public ManifestExporter() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

        this.canonicalJsonMapper = new ObjectMapper();
        this.canonicalJsonMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.canonicalJsonMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
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

    public <T> Optional<T> read(Path baseDir, String fileName, Class<T> bodyType) {
        Path input = baseDir.resolve(fileName);
        if (!Files.exists(input)) {
            return Optional.empty();
        }

        try {
            return Optional.of(yamlMapper.readValue(input.toFile(), bodyType));
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to read manifest " + fileName, e);
        }
    }

    public String sha256Hex(Object body) {
        try {
            String canonicalJson = canonicalJsonMapper.writeValueAsString(body);
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to compute manifest sha256", e);
        }
    }
}
