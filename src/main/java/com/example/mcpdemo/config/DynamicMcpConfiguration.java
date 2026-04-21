package com.example.mcpdemo.config;

import com.example.mcpdemo.manifest.generator.OpenApiContractReader;
import com.example.mcpdemo.manifest.generator.OpenApiToManifestGenerator;
import com.example.mcpdemo.manifest.io.AppManifestProperties;
import com.example.mcpdemo.manifest.io.ManifestExporter;
import com.example.mcpdemo.manifest.io.ManifestYamlLoader;
import com.example.mcpdemo.manifest.merge.ManifestMerger;
import com.example.mcpdemo.manifest.model.EffectiveManifest;
import com.example.mcpdemo.manifest.model.GeneratedManifest;
import com.example.mcpdemo.manifest.model.ManifestOverrides;
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
import org.springframework.core.io.ResourceLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
    List<McpServerFeatures.SyncResourceSpecification> dynamicResources(ManifestRegistry manifestRegistry) {
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
                            (exchange, request) -> new McpSchema.ReadResourceResult(
                                    List.of(new McpSchema.TextResourceContents(
                                            request.uri(),
                                            resource.mimeType(),
                                            resource.text()
                                    ))
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
                    var promptArguments = prompt.arguments().stream()
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
                                Map<String, Object> args = request.arguments();

                                String rendered = renderTemplate(prompt.template(), args);

                                var userMessage = new McpSchema.PromptMessage(
                                        McpSchema.Role.USER,
                                        new McpSchema.TextContent(rendered)
                                );

                                return new McpSchema.GetPromptResult(
                                        prompt.title(),
                                        List.of(userMessage)
                                );
                            }
                    );
                })
                .toList();
    }

    private String renderTemplate(String template, Map<String, Object> args) {
        String result = template;
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return result;
    }

    @Bean
    ApplicationRunner manifestDiagnostics(AppManifestProperties properties,
                                          GeneratedManifest generatedManifest,
                                          EffectiveManifest effectiveManifest,
                                          ManifestRegistry registry,
                                          ManifestExporter exporter) {
        return args -> {
            log.info("Generated tools: {}", generatedManifest.tools().size());
            log.info("Effective tools: {}", effectiveManifest.tools().size());
            registry.allTools().forEach(tool ->
                    log.info("Tool loaded -> name={}, handler={}", tool.name(), tool.binding().handler()));
            registry.allResources().forEach(resource ->
                    log.info("Resource loaded -> uri={}", resource.uri()));

            if (properties.getManifest().isExportOnStartup()) {
                Path exportDir = Path.of(properties.getManifest().getExportDir());
                exporter.export(exportDir, "mcp.generated.yaml", generatedManifest);
                exporter.export(exportDir, "mcp.effective.yaml", effectiveManifest);
                log.info("Manifest snapshots exported to {}", exportDir.toAbsolutePath());
            }
        };
    }
}
