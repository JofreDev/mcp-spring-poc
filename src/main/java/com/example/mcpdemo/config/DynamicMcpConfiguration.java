package com.example.mcpdemo.config;

import com.example.mcpdemo.manifest.generator.OpenApiContractReader;
import com.example.mcpdemo.manifest.generator.OpenApiToManifestGenerator;
import com.example.mcpdemo.manifest.io.AppManifestProperties;
import com.example.mcpdemo.manifest.io.ManifestExporter;
import com.example.mcpdemo.manifest.io.ManifestYamlLoader;
import com.example.mcpdemo.manifest.merge.ManifestMerger;
import com.example.mcpdemo.manifest.model.BindingDescriptor;
import com.example.mcpdemo.manifest.model.EffectiveManifest;
import com.example.mcpdemo.manifest.model.GeneratedManifest;
import com.example.mcpdemo.manifest.model.ManifestLock;
import com.example.mcpdemo.manifest.model.ManifestOverrides;
import com.example.mcpdemo.manifest.model.PromptArgumentDescriptor;
import com.example.mcpdemo.manifest.model.PromptDescriptor;
import com.example.mcpdemo.manifest.model.ResourceDescriptor;
import com.example.mcpdemo.manifest.model.ResourceOverride;
import com.example.mcpdemo.manifest.model.ToolDescriptor;
import com.example.mcpdemo.manifest.model.ToolOverride;
import com.example.mcpdemo.manifest.registry.ManifestRegistry;
import com.example.mcpdemo.runtime.DynamicToolCallback;
import com.example.mcpdemo.runtime.OperationInvokerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(AppManifestProperties.class)
public class DynamicMcpConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DynamicMcpConfiguration.class);

    @Bean
    GeneratedManifest generatedManifest(AppManifestProperties properties,
                                        OpenApiContractReader reader,
                                        OpenApiToManifestGenerator generator,
                                        ResourceLoader resourceLoader,
                                        ManifestYamlLoader loader,
                                        ManifestExporter exporter) throws Exception {
        String generatedLocation = optionalValue(properties.getManifest().getGeneratedLocation());
        if (generatedLocation != null) {
            GeneratedManifest externalGenerated = loader.loadOptional(generatedLocation, GeneratedManifest.class)
                    .orElse(null);
            if (externalGenerated != null) {
                return externalGenerated;
            }
            log.warn("Generated manifest location configured but not found/readable. Falling back to OpenAPI generation: {}",
                    generatedLocation);
        }

        ManifestLockMode lockMode = resolveLockMode(properties);
        Path exportDir = Path.of(properties.getManifest().getExportDir());
        String generatedFile = requiredValue("app.manifest.generated-file", properties.getManifest().getGeneratedFile());

        if (lockMode == ManifestLockMode.FROZEN) {
            return exporter.read(exportDir, generatedFile, GeneratedManifest.class)
                    .orElseThrow(() -> new IllegalStateException(
                            "Manifest lock mode 'frozen' requires existing generated snapshot: " + exportDir.resolve(generatedFile)
                    ));
        }

        String location = resourceLoader.getResource(properties.getOpenapi().getLocation()).getURL().toString();
        OpenAPI openAPI = reader.read(location);
        return generator.generate(openAPI);
    }

    @Bean
    ManifestOverrides manifestOverrides(AppManifestProperties properties,
                                        ManifestYamlLoader loader,
                                        GeneratedManifest generatedManifest) {
        ManifestOverridesMode overridesMode = resolveOverridesMode(properties);
        if (overridesMode == ManifestOverridesMode.OFF) {
            return emptyOverrides();
        }

        String overridesLocation = optionalValue(properties.getOverrides().getLocation());
        ManifestOverrides loaded = overridesLocation != null
                ? loader.loadOptional(overridesLocation, ManifestOverrides.class)
                .orElseGet(() -> {
                    log.warn("Overrides location not found. Continuing without overrides: {}", overridesLocation);
                    return emptyOverrides();
                })
                : emptyOverrides();

        ManifestOverrides normalized = normalizeOverrides(loaded);
        return applyOverrideCompatibilityPolicy(generatedManifest, normalized, overridesMode);
    }

    @Bean
    EffectiveManifest effectiveManifest(AppManifestProperties properties,
                                        ManifestYamlLoader loader,
                                        GeneratedManifest generatedManifest,
                                        ManifestOverrides manifestOverrides,
                                        ManifestMerger merger) {
        String effectiveLocation = optionalValue(properties.getManifest().getEffectiveLocation());
        if (effectiveLocation != null) {
            EffectiveManifest externalEffective = loader.loadOptional(effectiveLocation, EffectiveManifest.class)
                    .orElse(null);
            if (externalEffective != null) {
                return externalEffective;
            }
            log.warn("Effective manifest location configured but not found/readable. Falling back to merged effective manifest: {}",
                    effectiveLocation);
        }

        return merger.merge(generatedManifest, manifestOverrides);
    }

    @Bean
    ToolCallbackProvider dynamicToolProvider(ManifestRegistry manifestRegistry,
                                             OperationInvokerRegistry invokerRegistry,
                                             ObjectMapper objectMapper) {
        List<ToolCallback> callbacks = manifestRegistry.allTools().stream()
                .map(tool -> new DynamicToolCallback(tool, objectMapper, invokerRegistry))
                .map(ToolCallback.class::cast)
                .toList();

        return ToolCallbackProvider.from(callbacks);
    }

    @Bean
    List<McpServerFeatures.SyncResourceSpecification> dynamicResources(ManifestRegistry manifestRegistry,
                                                                       OperationInvokerRegistry invokerRegistry,
                                                                       ObjectMapper objectMapper) {
        return manifestRegistry.allResources().stream()
                .filter(resource -> !resource.excluded())
                .map(resource -> {
                    var mcpResource = new McpSchema.Resource(
                            resource.uri(),
                            resource.title(),
                            resource.description(),
                            resource.mimeType(),
                            null
                    );

                    return new McpServerFeatures.SyncResourceSpecification(
                            mcpResource,
                            (exchange, request) -> readResource(
                                    resource,
                                    request.uri(),
                                    invokerRegistry,
                                    objectMapper,
                                    true
                            )
                    );
                })
                .toList();
    }

    @Bean
    List<McpServerFeatures.SyncResourceTemplateSpecification> dynamicResourceTemplates(ManifestRegistry manifestRegistry,
                                                                                        OperationInvokerRegistry invokerRegistry,
                                                                                        ObjectMapper objectMapper) {
        return manifestRegistry.allResources().stream()
                .filter(resource -> !resource.excluded())
                .filter(this::hasUriTemplate)
                .map(resource -> {
                    var mcpResourceTemplate = McpSchema.ResourceTemplate.builder()
                            .uriTemplate(resource.uriTemplate())
                            .name(resource.uri())
                            .title(resource.title())
                            .description(resource.description())
                            .mimeType(resource.mimeType())
                            .build();

                    return new McpServerFeatures.SyncResourceTemplateSpecification(
                            mcpResourceTemplate,
                            (exchange, request) -> readResource(
                                    resource,
                                    request.uri(),
                                    invokerRegistry,
                                    objectMapper,
                                    false
                            )
                    );
                })
                .toList();
    }

    @Bean
    List<McpServerFeatures.SyncPromptSpecification> dynamicPrompts(ManifestRegistry manifestRegistry) {
        return manifestRegistry.allPrompts().stream()
                .filter(prompt -> !prompt.excluded())
                .map(prompt -> {
                    var promptArguments = promptArgumentsOrEmpty(prompt).stream()
                            .map(arg -> new McpSchema.PromptArgument(
                                    arg.name(),
                                    arg.description(),
                                    arg.required()
                            ))
                            .toList();

                    var mcpPrompt = new McpSchema.Prompt(
                            prompt.name(),
                            prompt.description(),
                            promptArguments
                    );

                    return new McpServerFeatures.SyncPromptSpecification(
                            mcpPrompt,
                            (exchange, request) -> {
                                Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();

                                String rendered = renderTemplate(prompt.template(), args);

                                var userMessage = new McpSchema.PromptMessage(
                                        McpSchema.Role.USER,
                                        new McpSchema.TextContent(rendered)
                                );

                                return new McpSchema.GetPromptResult(
                                        promptTitle(prompt),
                                        List.of(userMessage)
                                );
                            }
                    );
                })
                .toList();
    }

    private String renderTemplate(String template, Map<String, Object> args) {
        String result = template != null ? template : "";
        if (args == null || args.isEmpty()) {
            return result;
        }

        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String placeholder = "{{" + key + "}}";
            result = result.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String promptTitle(PromptDescriptor prompt) {
        if (prompt.title() != null && !prompt.title().isBlank()) {
            return prompt.title();
        }
        return prompt.name();
    }

    private List<PromptArgumentDescriptor> promptArgumentsOrEmpty(PromptDescriptor prompt) {
        if (prompt.arguments() == null || prompt.arguments().isEmpty()) {
            return List.of();
        }
        return prompt.arguments().stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean hasUriTemplate(ResourceDescriptor resource) {
        return resource.uriTemplate() != null && !resource.uriTemplate().isBlank();
    }

    private McpSchema.ReadResourceResult readResource(ResourceDescriptor resource,
                                                      String requestedUri,
                                                      OperationInvokerRegistry invokerRegistry,
                                                      ObjectMapper objectMapper,
                                                      boolean fallbackToStaticText) {
        String resourceUri = resource.uri();
        try {
            String resolvedText = invokerRegistry.findResourceExecution(resourceUri)
                    .map(invoker -> invoker.invoke(requestedUri))
                    .map(payload -> toResourceText(payload, objectMapper, resourceUri))
                    .orElse(resource.text());

            return toReadResourceResult(requestedUri, resource.mimeType(), resolvedText);
        }
        catch (IllegalArgumentException e) {
            if (!fallbackToStaticText || resource.text() == null || !isMissingResourceQueryParameterError(e)) {
                throw e;
            }
            log.debug("Resource execution fallback to static content: uri={}, requestedUri={}, reason={}",
                    resourceUri,
                    requestedUri,
                    e.getMessage());
            return toReadResourceResult(requestedUri, resource.mimeType(), resource.text());
        }
    }

    private boolean isMissingResourceQueryParameterError(IllegalArgumentException error) {
        return error.getMessage() != null
                && error.getMessage().startsWith("Missing required resource query parameter:");
    }

    private McpSchema.ReadResourceResult toReadResourceResult(String uri, String mimeType, String text) {
        String content = text != null ? text : "";
        return new McpSchema.ReadResourceResult(
                List.of(new McpSchema.TextResourceContents(
                        uri,
                        mimeType,
                        content
                ))
        );
    }

    private String toResourceText(Object payload, ObjectMapper objectMapper, String resourceUri) {
        if (payload == null) {
            return "";
        }
        if (payload instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to serialize resource payload for " + resourceUri, e);
        }
    }

    @Bean
    ApplicationRunner manifestFailFastValidation(ManifestRegistry registry,
                                                 EffectiveManifest effectiveManifest,
                                                 OperationInvokerRegistry invokerRegistry,
                                                 Environment environment) {
        return args -> {
            validateToolCapability(registry, environment);
            validateToolBindings(effectiveManifest, invokerRegistry);
            validateResourceReadability(effectiveManifest, invokerRegistry);
            validatePromptArguments(effectiveManifest);
        };
    }

    private void validateToolCapability(ManifestRegistry registry, Environment environment) {
        boolean toolCapabilityEnabled = environment.getProperty(
                "spring.ai.mcp.server.capabilities.tool",
                Boolean.class,
                false
        );

        if (!toolCapabilityEnabled && !registry.allTools().isEmpty()) {
            throw new IllegalStateException(
                    "Tools are present in the effective manifest, but spring.ai.mcp.server.capabilities.tool=false"
            );
        }
    }

    private void validateToolBindings(EffectiveManifest effectiveManifest, OperationInvokerRegistry invokerRegistry) {
        List<ToolDescriptor> tools = effectiveManifest.tools() != null ? effectiveManifest.tools() : List.of();
        for (ToolDescriptor tool : tools) {
            if (tool == null || tool.excluded()) {
                continue;
            }

            String toolName = requiredValue("tool.name", tool.name());
            if (tool.binding() == null) {
                throw new IllegalStateException("Tool '" + toolName + "' has no binding configured");
            }

            String executionKey = resolveToolExecutionKey(tool);
            try {
                invokerRegistry.requiredToolExecution(executionKey);
            }
            catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "No tool execution registered for tool '" + toolName + "' with execution key '" + executionKey + "'",
                        e
                );
            }
        }
    }

    private String resolveToolExecutionKey(ToolDescriptor tool) {
        BindingDescriptor binding = tool.binding();
        if (binding != null && binding.handler() != null && !binding.handler().isBlank()) {
            return binding.handler();
        }
        return requiredValue("tool.name", tool.name());
    }

    private void validateResourceReadability(EffectiveManifest effectiveManifest, OperationInvokerRegistry invokerRegistry) {
        List<ResourceDescriptor> resources = effectiveManifest.resources() != null ? effectiveManifest.resources() : List.of();
        for (ResourceDescriptor resource : resources) {
            if (resource == null || resource.excluded()) {
                continue;
            }

            String resourceUri = requiredValue("resource.uri", resource.uri());
            boolean hasExecution = invokerRegistry.findResourceExecution(resourceUri).isPresent();
            boolean hasStaticText = resource.text() != null && !resource.text().isBlank();

            if (hasUriTemplate(resource) && !hasExecution) {
                throw new IllegalStateException(
                        "No resource execution registered for templated resource '" + resourceUri + "'"
                );
            }

            if (!hasExecution && !hasStaticText) {
                throw new IllegalStateException(
                        "Resource '" + resourceUri + "' has no execution and no static text fallback"
                );
            }
        }
    }

    private void validatePromptArguments(EffectiveManifest effectiveManifest) {
        List<PromptDescriptor> prompts = effectiveManifest.prompts() != null ? effectiveManifest.prompts() : List.of();
        for (PromptDescriptor prompt : prompts) {
            if (prompt == null || prompt.excluded()) {
                continue;
            }

            requiredValue("prompt.name", prompt.name());
            Set<String> argumentNames = new HashSet<>();
            List<PromptArgumentDescriptor> promptArguments = prompt.arguments() != null ? prompt.arguments() : List.of();
            for (PromptArgumentDescriptor argument : promptArguments) {
                if (argument == null) {
                    throw new IllegalStateException("Prompt '" + prompt.name() + "' has a null argument entry");
                }
                String argumentName = requiredValue("prompt.argument.name", argument.name());
                if (!argumentNames.add(argumentName)) {
                    throw new IllegalStateException(
                            "Prompt '" + prompt.name() + "' has duplicated argument name: " + argumentName
                    );
                }
            }
        }
    }

    private String requiredValue(String fieldName, String value) {
        if (value == null) {
            throw new IllegalStateException(fieldName + " cannot be null");
        }
        if (value.isBlank()) {
            throw new IllegalStateException(fieldName + " cannot be blank");
        }
        return value;
    }

    private String optionalValue(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ManifestOverrides emptyOverrides() {
        return new ManifestOverrides(Map.of(), Map.of(), List.of());
    }

    private ManifestOverrides normalizeOverrides(ManifestOverrides overrides) {
        if (overrides == null) {
            return emptyOverrides();
        }

        Map<String, ToolOverride> tools = overrides.tools() != null ? overrides.tools() : Map.of();
        Map<String, ResourceOverride> resources = overrides.resources() != null ? overrides.resources() : Map.of();
        List<PromptDescriptor> prompts = overrides.prompts() != null ? overrides.prompts() : List.of();

        return new ManifestOverrides(tools, resources, prompts);
    }

    private ManifestOverrides applyOverrideCompatibilityPolicy(GeneratedManifest generatedManifest,
                                                               ManifestOverrides overrides,
                                                               ManifestOverridesMode overridesMode) {
        if (overridesMode == ManifestOverridesMode.OFF) {
            return emptyOverrides();
        }

        Set<String> generatedToolNames = generatedManifest.tools() == null
                ? Set.of()
                : generatedManifest.tools().stream()
                .filter(Objects::nonNull)
                .map(ToolDescriptor::name)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

        Set<String> generatedResourceUris = generatedManifest.resources() == null
                ? Set.of()
                : generatedManifest.resources().stream()
                .filter(Objects::nonNull)
                .map(ResourceDescriptor::uri)
                .filter(Objects::nonNull)
                .filter(uri -> !uri.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

        Set<String> unknownTools = overrides.tools().keySet().stream()
                .filter(key -> key != null && !key.isBlank())
                .filter(key -> !generatedToolNames.contains(key))
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

        Set<String> unknownResources = overrides.resources().keySet().stream()
                .filter(key -> key != null && !key.isBlank())
                .filter(key -> !generatedResourceUris.contains(key))
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

        if ((overridesMode == ManifestOverridesMode.STRICT) && (!unknownTools.isEmpty() || !unknownResources.isEmpty())) {
            throw new IllegalStateException("Overrides validation failed. Unknown tool overrides=" + unknownTools
                    + ", unknown resource overrides=" + unknownResources);
        }

        if (overridesMode == ManifestOverridesMode.WARN) {
            if (!unknownTools.isEmpty()) {
                log.warn("Ignoring unknown tool overrides: {}", unknownTools);
            }
            if (!unknownResources.isEmpty()) {
                log.warn("Ignoring unknown resource overrides: {}", unknownResources);
            }
        }

        if (overridesMode == ManifestOverridesMode.WARN) {
            Map<String, ToolOverride> filteredTools = new LinkedHashMap<>(overrides.tools());
            Map<String, ResourceOverride> filteredResources = new LinkedHashMap<>(overrides.resources());
            unknownTools.forEach(filteredTools::remove);
            unknownResources.forEach(filteredResources::remove);
            return new ManifestOverrides(filteredTools, filteredResources, overrides.prompts());
        }

        return overrides;
    }

    private ManifestLockMode resolveLockMode(AppManifestProperties properties) {
        return ManifestLockMode.from(requiredValue("app.manifest.lock-mode", properties.getManifest().getLockMode()));
    }

    private ManifestOverridesMode resolveOverridesMode(AppManifestProperties properties) {
        return ManifestOverridesMode.from(requiredValue("app.manifest.overrides-mode", properties.getManifest().getOverridesMode()));
    }

    @Bean
    ApplicationRunner manifestDiagnostics(AppManifestProperties properties,
                                          GeneratedManifest generatedManifest,
                                          EffectiveManifest effectiveManifest,
                                          ManifestRegistry registry,
                                          ManifestExporter exporter) {
        return args -> {
            int generatedTools = generatedManifest.tools() != null ? generatedManifest.tools().size() : 0;
            int effectiveTools = effectiveManifest.tools() != null ? effectiveManifest.tools().size() : 0;
            ManifestLockMode lockMode = resolveLockMode(properties);

            Path exportDir = Path.of(properties.getManifest().getExportDir());
            String generatedFile = requiredValue("app.manifest.generated-file", properties.getManifest().getGeneratedFile());
            String effectiveFile = requiredValue("app.manifest.effective-file", properties.getManifest().getEffectiveFile());
            String lockFile = requiredValue("app.manifest.lock-file", properties.getManifest().getLockFile());

            log.info("Generated tools: {}", generatedTools);
            log.info("Effective tools: {}", effectiveTools);
            log.info("Manifest lock mode: {}", lockMode.configValue());
            registry.allTools().forEach(tool ->
                    log.info(
                            "Tool loaded -> name={}, handler={}",
                            tool.name(),
                            tool.binding() != null ? tool.binding().handler() : "<missing>"
                    ));
            registry.allResources().forEach(resource ->
                    log.info("Resource loaded -> uri={}, uriTemplate={}",
                            resource.uri(),
                            resource.uriTemplate()));

            ManifestLock currentLock = buildManifestLock(generatedManifest, effectiveManifest, exporter);
            if (lockMode.isEnabled()) {
                verifyManifestLock(
                        exportDir,
                        lockFile,
                        currentLock,
                        exporter,
                        lockMode
                );
            }

            if (properties.getManifest().isExportOnStartup()) {
                log.warn("Manifest export-on-startup is enabled. Keep this disabled in stateless deployments and generate snapshots/lock in CI/build.");
                exporter.export(exportDir, generatedFile, generatedManifest);
                exporter.export(exportDir, effectiveFile, effectiveManifest);
                exporter.export(exportDir, lockFile, currentLock);
                log.info("Manifest snapshots exported to {}", exportDir.toAbsolutePath());
            }
        };
    }

    private ManifestLock buildManifestLock(GeneratedManifest generatedManifest,
                                           EffectiveManifest effectiveManifest,
                                           ManifestExporter exporter) {
        int generatedTools = generatedManifest.tools() != null ? generatedManifest.tools().size() : 0;
        int generatedResources = generatedManifest.resources() != null ? generatedManifest.resources().size() : 0;
        int effectiveTools = effectiveManifest.tools() != null ? effectiveManifest.tools().size() : 0;
        int effectiveResources = effectiveManifest.resources() != null ? effectiveManifest.resources().size() : 0;
        int effectivePrompts = effectiveManifest.prompts() != null ? effectiveManifest.prompts().size() : 0;

        return new ManifestLock(
                1,
                "SHA-256",
                exporter.sha256Hex(generatedManifest),
                exporter.sha256Hex(effectiveManifest),
                generatedTools,
                generatedResources,
                effectiveTools,
                effectiveResources,
                effectivePrompts
        );
    }

    private void verifyManifestLock(Path exportDir,
                                    String lockFile,
                                    ManifestLock currentLock,
                                    ManifestExporter exporter,
                                    ManifestLockMode lockMode) {
        ManifestLock storedLock = exporter.read(exportDir, lockFile, ManifestLock.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Manifest lock mode '" + lockMode.configValue()
                                + "' requires lock file: " + exportDir.resolve(lockFile)
                ));

        if (!storedLock.equals(currentLock)) {
            throw new IllegalStateException(
                    "Manifest lock mismatch for " + exportDir.resolve(lockFile)
                            + ". expected=" + storedLock
                            + ", current=" + currentLock
            );
        }
    }

    private enum ManifestLockMode {
        OFF("off"),
        VERIFY("verify"),
        FROZEN("frozen");

        private final String configValue;

        ManifestLockMode(String configValue) {
            this.configValue = configValue;
        }

        static ManifestLockMode from(String rawValue) {
            String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
            for (ManifestLockMode value : values()) {
                if (value.configValue.equals(normalized)) {
                    return value;
                }
            }
            throw new IllegalStateException(
                    "Unsupported app.manifest.lock-mode='" + rawValue + "'. Supported values: off, verify, frozen"
            );
        }

        boolean isEnabled() {
            return this != OFF;
        }

        String configValue() {
            return configValue;
        }
    }

    private enum ManifestOverridesMode {
        OFF("off"),
        STRICT("strict"),
        WARN("warn");

        private final String configValue;

        ManifestOverridesMode(String configValue) {
            this.configValue = configValue;
        }

        static ManifestOverridesMode from(String rawValue) {
            String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
            for (ManifestOverridesMode value : values()) {
                if (value.configValue.equals(normalized)) {
                    return value;
                }
            }
            throw new IllegalStateException(
                    "Unsupported app.manifest.overrides-mode='" + rawValue + "'. Supported values: off, strict, warn"
            );
        }
    }
}
