# mcp-manifest-poc

PoC en Java 21 + Spring Boot + Spring AI para:

1. leer un contrato OpenAPI,
2. generar un modelo MCP canónico en memoria,
3. aplicar `overrides` humanos,
4. exportar `generated.yaml` y `effective.yaml` opcionalmente,
5. registrar tools dinámicas en Spring AI MCP.

## Idea central

- **OpenAPI** = descubrimiento técnico.
- **Overrides YAML** = intención humana editable.
- **Effective manifest** = modelo final en memoria.
- **Spring AI** = exposición MCP.

## Flujo

```text
OpenAPI -> GeneratedManifest -> Overrides -> EffectiveManifest -> ToolCallbackProvider -> MCP Server
```

## Objetivo PoC

- Genera **tools** automáticamente desde operaciones OpenAPI.
- Genera **resources documentales** simples (`openapi://summary`, `openapi://operations/...`) y las deja en el modelo efectivo.
- Permite especificar prompts en el `mcp-overrides.yaml`
- Permite mejorar nombre, descripción, anotaciones y binding con `mcp-overrides.yaml`.
- Hace el binding final a **casos de uso Spring** a través de `OperationInvokerRegistry`.
- Exporta snapshots YAML a `build/mcp-manifests/`.

## Lo dejado intencionalmente simple

- El `inputSchema` es general, no exhaustivo para todos los esquemas OpenAPI.
- El `requestBody` se modela de forma básica.
- Los **resources** quedan generados y persistidos en el modelo, pero en este PoC la exposición runtime se centra en **tools**, que es la vía programática más directa y documentada en Spring AI.

## Archivos importantes

- `src/main/resources/openapi/demo-contract.yaml`
- `src/main/resources/mcp-overrides.yaml`
- `OpenApiToManifestGenerator`
- `ManifestMerger`
- `ManifestRegistry`
- `OperationInvokerRegistry`
- `DynamicToolCallback`
- `DynamicMcpConfiguration`

## Probar : 

1. Ajustar el contrato OpenAPI.
2. Ajustar `mcp-overrides.yaml`.
3. Levantar la app.
4. Revisa los YAML exportados en `build/mcp-manifests/`.
5. Conectar un cliente MCP al endpoint Streamable HTTP.
