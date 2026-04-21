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

    /*
    * Registro de la tool
    * La tool se vuelve descubrible
    * */
    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(toJson(tool.inputSchema()))
                .build();
    }
    /*
     * Se define que hacer cuando la tool es llamada
     * toolInput : El o los argumentos .
        * Acá nace args, ejemplo :
        * toolInput = {"customerId":"C-100"}
        * si Map<String, Object> args = objectMapper.readValue(toolInput, new TypeReference<>() {});
        * entonces args = "customerId" -> "C-100"
     */

    @Override
    public String call(String toolInput) {
        // tool.binding().handler() -> Saca del manifiesto el handler logico. Ej tool.binding().handler() -> "customers.getById"
        // invokerRegistry.required(...) -> Busca en el OperationInvokerRegistry el invocador asociado a ese handler.

        try {
            Map<String, Object> args = objectMapper.readValue(toolInput, new TypeReference<>() {});
            // Hace el get que permite traer el useCase/bean que va a ejecutar y lo ejecuta con sus correspondientes argumentos
            Object result = invokerRegistry.required(tool.binding().handler()).invoke(args);
            // Ej "customers.getById" → lambda que llama customerQueryUseCase.getById(...)
            // .invoke(args) ->  Ejecuta la lambda o invocador real pasándole el mapa de argumentos. !!!!

            return objectMapper.writeValueAsString(result);
        }
        catch (Exception e) {
            throw new RuntimeException("Error executing tool: " + tool.name(), e);
        }
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
