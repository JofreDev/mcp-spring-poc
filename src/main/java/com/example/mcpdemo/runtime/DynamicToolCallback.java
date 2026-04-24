package com.example.mcpdemo.runtime;

import com.example.mcpdemo.manifest.model.ToolDescriptor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

public class DynamicToolCallback implements ToolCallback {

    private final ToolDescriptor tool;
    private final ObjectMapper objectMapper;
    private final OperationInvokerRegistry invokerRegistry;

    public DynamicToolCallback(ToolDescriptor tool,
                               ObjectMapper objectMapper,
                               OperationInvokerRegistry invokerRegistry) {
        this.tool = tool;
        this.objectMapper = objectMapper;
        this.invokerRegistry = invokerRegistry;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(toJson(tool.inputSchema()))
                .build();
    }

    @Override
    public String call(String toolInput) {
        try {
            Map<String, Object> args = objectMapper.readValue(toolInput, new TypeReference<>() {});
            Object result = invokerRegistry.requiredToolExecution(resolveExecutionKey()).invoke(args);

            return objectMapper.writeValueAsString(result);
        }
        catch (Exception e) {
            throw new RuntimeException("Error executing tool: " + tool.name(), e);
        }
    }

    private String resolveExecutionKey() {
        if (tool.binding() != null && tool.binding().handler() != null && !tool.binding().handler().isBlank()) {
            return tool.binding().handler();
        }
        if (tool.name() == null || tool.name().isBlank()) {
            throw new IllegalStateException("Tool must define name or binding.handler");
        }
        return tool.name();
    }

    private String toJson(Map<String, Object> schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to serialize input schema for tool " + tool.name(), e);
        }
    }
}
