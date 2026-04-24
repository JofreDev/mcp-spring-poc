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
import com.example.mcpdemo.manifest.model.ManifestOverrides;
import com.example.mcpdemo.manifest.model.PromptArgumentDescriptor;
import com.example.mcpdemo.manifest.model.PromptDescriptor;
import com.example.mcpdemo.manifest.model.ResourceDescriptor;
import com.example.mcpdemo.manifest.model.ToolDescriptor;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(AppManifestProperties.class)
public class DynamicMcpConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DynamicMcpConfiguration.class);

    @Bean
    OpenAPI openAPI(AppManifestProperties properties,
                    OpenApiContractReader reader,
                    ResourceLoader resourceLoader) throws Exception {
        String location = resourceLoader.getResource(properties.getOpenapi().getLocation()).getURL().toString();
        return reader.read(location);
    }

    @Bean
    GeneratedManifest generatedManifest(OpenAPI openAPI,
                                        OpenApiToManifestGenerator generator) {
        return generator.generate(openAPI);
    }

    @Bean
    ManifestOverrides manifestOverrides(AppManifestProperties properties,
                                        ManifestYamlLoader loader) {
        return loader.loadOverrides(properties.getOverrides().getLocation());
    }

    @Bean
    EffectiveManifest effectiveManifest(GeneratedManifest generatedManifest,
                                        ManifestOverrides manifestOverrides,
                                        ManifestMerger merger) {
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

    @Bean
    ApplicationRunner manifestDiagnostics(AppManifestProperties properties,
                                          GeneratedManifest generatedManifest,
                                          EffectiveManifest effectiveManifest,
                                          ManifestRegistry registry,
                                          ManifestExporter exporter) {
        return args -> {
            int generatedTools = generatedManifest.tools() != null ? generatedManifest.tools().size() : 0;
            int effectiveTools = effectiveManifest.tools() != null ? effectiveManifest.tools().size() : 0;

            log.info("Generated tools: {}", generatedTools);
            log.info("Effective tools: {}", effectiveTools);
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

            if (properties.getManifest().isExportOnStartup()) {
                Path exportDir = Path.of(properties.getManifest().getExportDir());
                exporter.export(exportDir, "mcp.generated.yaml", generatedManifest);
                exporter.export(exportDir, "mcp.effective.yaml", effectiveManifest);
                log.info("Manifest snapshots exported to {}", exportDir.toAbsolutePath());
            }
        };
    }
}
