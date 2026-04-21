package com.example.mcpdemo.manifest.generator;

import com.example.mcpdemo.manifest.model.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class OpenApiToManifestGenerator {

    public GeneratedManifest generate(OpenAPI openAPI) {
        List<ToolDescriptor> tools = new ArrayList<>();
        List<ResourceDescriptor> resources = new ArrayList<>();

        if (openAPI.getPaths() == null) {
            return new GeneratedManifest(List.of(), List.of());
        }

        resources.add(new ResourceDescriptor(
                "openapi://summary",
                "OpenAPI Summary",
                "Resumen básico del contrato OpenAPI cargado",
                "text/plain",
                buildSummary(openAPI),
                false
        ));

        openAPI.getPaths().forEach((path, pathItem) -> {
            visitOperation("GET", path, pathItem.getGet(), tools, resources);
            visitOperation("POST", path, pathItem.getPost(), tools, resources);
            visitOperation("PUT", path, pathItem.getPut(), tools, resources);
            visitOperation("PATCH", path, pathItem.getPatch(), tools, resources);
            visitOperation("DELETE", path, pathItem.getDelete(), tools, resources);
        });

        return new GeneratedManifest(tools, resources);
    }

    private void visitOperation(String httpMethod,
                                String path,
                                Operation operation,
                                List<ToolDescriptor> tools,
                                List<ResourceDescriptor> resources) {
        if (operation == null) {
            return;
        }

        String operationId = operation.getOperationId() != null ? operation.getOperationId() : deriveOperationName(httpMethod, path);
        String title = operation.getSummary() != null ? operation.getSummary() : operationId;
        String description = operation.getDescription() != null ? operation.getDescription() : title;
        String handler = readHandler(operation, operationId);

        tools.add(new ToolDescriptor(
                operationId,
                operationId,
                title,
                description,
                buildInputSchema(operation),
                buildOutputSchema(operation.getResponses()),
                AnnotationsDescriptor.defaultsForHttpMethod(httpMethod),
                new BindingDescriptor(handler, "spring-bean"),
                false
        ));

        resources.add(new ResourceDescriptor(
                "openapi://operations/" + operationId,
                "OpenAPI Operation " + operationId,
                "Documentación breve de la operación " + operationId,
                "text/plain",
                buildOperationDoc(httpMethod, path, operation),
                false
        ));
    }

    private String readHandler(Operation operation, String defaultOperationId) {
        if (operation.getExtensions() != null && operation.getExtensions().get("x-handler") != null) {
            return String.valueOf(operation.getExtensions().get("x-handler"));
        }
        return defaultOperationId;
    }

    private Map<String, Object> buildInputSchema(Operation operation) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                Map<String, Object> property = new LinkedHashMap<>();
                Schema<?> schema = parameter.getSchema();
                property.put("type", schema != null && schema.getType() != null ? schema.getType() : "string");
                if (parameter.getDescription() != null) {
                    property.put("description", parameter.getDescription());
                }
                if (schema != null && schema.getEnum() != null && !schema.getEnum().isEmpty()) {
                    property.put("enum", schema.getEnum());
                }
                properties.put(parameter.getName(), property);
                if (Boolean.TRUE.equals(parameter.getRequired())) {
                    required.add(parameter.getName());
                }
            }
        }

        RequestBody requestBody = operation.getRequestBody();
        if (requestBody != null && requestBody.getContent() != null) {
            Schema<?> bodySchema = requestBody.getContent().values().stream()
                    .findFirst()
                    .map(mediaType -> mediaType.getSchema())
                    .orElse(null);

            if (bodySchema != null) {
                properties.put("body", simplifySchema(bodySchema));
                if (Boolean.TRUE.equals(requestBody.getRequired())) {
                    required.add("body");
                }
            }
        }

        root.put("properties", properties);
        root.put("required", required);
        return root;
    }

    private Map<String, Object> buildOutputSchema(ApiResponses responses) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("description", "Respuesta simplificada inferida desde OpenAPI para PoC");

        if (responses == null || responses.isEmpty()) {
            return root;
        }

        responses.forEach((status, response) -> {
            if (response != null && response.getContent() != null) {
                response.getContent().forEach((mime, mediaType) -> {
                    Schema<?> schema = mediaType.getSchema();
                    if (schema != null) {
                        root.put("x-first-response-status", status);
                        root.put("x-first-response-mimeType", mime);
                        root.put("x-first-response-schema", simplifySchema(schema));
                    }
                });
            }
        });
        return root;
    }

    private Map<String, Object> simplifySchema(Schema<?> schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        String type = schema.getType();
        result.put("type", type != null ? type : "object");

        if (schema.getDescription() != null) {
            result.put("description", schema.getDescription());
        }

        if (schema instanceof ArraySchema arraySchema) {
            Schema<?> itemSchema = arraySchema.getItems();
            result.put("items", itemSchema != null ? simplifySchema(itemSchema) : Map.of("type", "object"));
            return result;
        }

        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            Map<String, Object> props = new LinkedHashMap<>();
            schema.getProperties().forEach((key, value) -> props.put(String.valueOf(key), simplifySchema((Schema<?>) value)));
            result.put("properties", props);
        }

        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            result.put("enum", schema.getEnum());
        }

        return result;
    }

    private String buildSummary(OpenAPI openAPI) {
        return "title=" + optional(openAPI.getInfo() != null ? openAPI.getInfo().getTitle() : null)
                + ", version=" + optional(openAPI.getInfo() != null ? openAPI.getInfo().getVersion() : null)
                + ", paths=" + openAPI.getPaths().size();
    }

    private String buildOperationDoc(String httpMethod, String path, Operation operation) {
        return "operationId=" + optional(operation.getOperationId()) + "\n"
                + "method=" + httpMethod + "\n"
                + "path=" + path + "\n"
                + "summary=" + optional(operation.getSummary()) + "\n"
                + "description=" + optional(operation.getDescription());
    }

    private String deriveOperationName(String httpMethod, String path) {
        String normalized = path.replace("/", "_")
                .replace("{", "")
                .replace("}", "")
                .replace("-", "_");
        return httpMethod.toLowerCase(Locale.ROOT) + normalized;
    }

    private String optional(String value) {
        return value == null ? "" : value;
    }
}
