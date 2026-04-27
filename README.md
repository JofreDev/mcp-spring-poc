# mcp-manifest-poc

`mcp-manifest-poc` es una prueba de concepto que demuestra cómo construir un servidor MCP a partir de un contrato OpenAPI, aplicando una capa de personalización declarativa y registrando herramientas dinámicas sobre Spring AI.

## Propósito

El proyecto implementa un flujo de trabajo orientado a contrato con tres metas:

1. transformar OpenAPI en un manifiesto base (`GeneratedManifest`),
2. aplicar intención humana mediante overrides YAML (`ManifestOverrides`),
3. exponer el manifiesto efectivo en runtime como tools, resources y prompts MCP.

## Tecnologías utilizadas

- Java 21 (toolchain de Gradle).
- Spring Boot 3.4.4.
- Spring AI 1.1.4 (`spring-ai-starter-mcp-server-webflux`).
- Swagger Parser 2.1.25 para lectura de contratos OpenAPI 3.
- Jackson YAML para carga y exportación de manifiestos.
- Gradle Wrapper (`./gradlew`, `gradlew.bat`) como mecanismo de build.

## Lógica del proyecto

### Flujo principal

```text
OpenAPI
  -> OpenApiContractReader
  -> OpenApiToManifestGenerator
  -> GeneratedManifest
  + ManifestOverrides (mcp-overrides.yaml)
  -> ManifestMerger
  -> EffectiveManifest
  -> ManifestRegistry
  -> DynamicMcpConfiguration
  -> MCP Server (/mcp)
```

### Responsabilidad de cada módulo

| Módulo | Responsabilidad |
|---|---|
| `manifest/generator` | Lee OpenAPI y genera tools/resources base con esquemas simplificados. |
| `manifest/merge` | Fusiona el manifiesto generado con overrides de negocio. |
| `manifest/io` | Carga overrides desde YAML y exporta snapshots de manifiesto. |
| `manifest/registry` | Mantiene en memoria las definiciones efectivas de tools/resources/prompts. |
| `runtime` | Convierte cada tool en `ToolCallback` y delega ejecución a invocadores registrados. |
| `sample/usecase` | Implementaciones de ejemplo para simular lógica de negocio. |

### Conversión de OpenAPI a definiciones MCP

`OpenApiToManifestGenerator` recorre cada operación HTTP y construye:

- una `ToolDescriptor` con `name`, `description`, `inputSchema`, `outputSchema`, `annotations` y `binding`;
- un `ResourceDescriptor` documental por operación (`openapi://operations/{operationId}`), identificado por URI;
- un recurso resumen (`openapi://summary`) con metadatos básicos del contrato.

En tools, el `binding.handler` se toma desde `x-handler` cuando existe; en caso contrario utiliza `operationId`. El modo generado por defecto es `registry`, pensado para resolución vía contributors. En resources, la identificación se hace por `uri` (y opcionalmente `uriTemplate` para variantes parametrizadas), alineado con MCP.

Cuando una operación OpenAPI tiene parámetros de query, el generador puede producir un `uriTemplate` para el resource. Ese template también puede sobrescribirse desde `mcp-overrides.yaml`.

### Aplicación de overrides

`ManifestMerger` aplica personalizaciones desde `mcp-overrides.yaml` sobre tools y resources:

- nombre, título y descripción,
- anotaciones de comportamiento (`readOnlyHint`, `idempotentHint`, etc.),
- exclusión de elementos (`exclude`),
- parámetros de binding para tools y `uriTemplate` para resources,
- prompts definidos por negocio.

El resultado se almacena como `EffectiveManifest`.

### Binding modes (iteración 3)

La ejecución ahora puede resolverse declarativamente por `binding.mode`:

- `registry`: mantiene el comportamiento clásico con `OperationExecutionContributor`.
- `spring-bean`: invoca métodos de beans Spring con mapeo de argumentos declarativo.
- `http`: invoca endpoints HTTP externos con configuración declarativa.

Ejemplo en overrides:

```yaml
tools:
  getCustomerById:
    binding:
      handler: customers.getById
      mode: spring-bean
      options:
        bean: customerQueryUseCase
        method: getById
        args: [customerId]
```

Para resources se soporta el mismo esquema. En `args` puedes usar `_uri` para enviar la URI solicitada completa al método de destino.

### Ejecución dinámica de tools

`DynamicToolCallback` define dos comportamientos:

1. publicación de metadatos de la tool (nombre, descripción y esquema de entrada),
2. ejecución de la tool al recibir JSON de entrada, resolviendo el handler en `OperationInvokerRegistry`.

`OperationInvokerRegistry` define el contrato de resolución. El mapeo concreto combina dos fuentes:

1. resolución declarativa basada en `binding.mode` (`ManifestDrivenOperationInvokerRegistry`),
2. fallback por contributors (`CompositeOperationInvokerRegistry`).

Así puedes mover gradualmente tus operaciones a manifiestos sin perder compatibilidad con contributors existentes.

`AbstractOperationExecutionContributor` incluye helpers para tratar argumentos complejos sin casts manuales: `toolArgAs(...)`, `optionalToolArgAs(...)`, `toolArgAsJson(...)`, `toolBodyAs(...)`, `toolBodyAsJson(...)`, `optionalResourceQueryParam(...)` y `requiredResourceQueryParam(...)`.

### Exposición MCP

`DynamicMcpConfiguration` registra en runtime:

- `ToolCallbackProvider` para tools dinámicas,
- `SyncResourceSpecification` para resources,
- `SyncResourceTemplateSpecification` para resources parametrizables vía URI,
- `SyncPromptSpecification` para prompts con plantillas tipo `{{argumento}}`.

Además, si está habilitado `app.manifest.export-on-startup`, exporta:

- `build/mcp-manifests/mcp.generated.yaml`
- `build/mcp-manifests/mcp.effective.yaml`
- `build/mcp-manifests/mcp.lock.yaml`

El archivo `mcp.lock.yaml` se usa en runtime cuando `app.manifest.lock-mode` es `verify` o `frozen`
para proteger consistencia entre contrato, manifiesto generado y manifiesto efectivo.

## Forma de trabajo recomendada

El proyecto está diseñado para una iteración corta basada en contrato:

1. definir o ajustar operaciones en `src/main/resources/openapi/demo-contract.yaml`;
2. declarar intención funcional en `src/main/resources/mcp-overrides.yaml`;
3. mapear ejecución en `binding.mode` (declarativo) o, si hace falta lógica custom, en contributors;
4. generar y revisar snapshots en CI/build (o localmente si habilitas `export-on-startup`);
5. validar desde un cliente MCP contra el endpoint `/mcp`.

Este ciclo separa claramente lo técnico (contrato OpenAPI), lo semántico (overrides) y lo ejecutable (bindings declarativos y/o use cases Spring).

Ejemplo de contributor personalizado:

```java
@Component
public class BillingExecutionContributor extends AbstractOperationExecutionContributor {

    private final BillingUseCase billingUseCase;

    public BillingExecutionContributor(BillingUseCase billingUseCase) {
        this.billingUseCase = billingUseCase;
    }

    @Override
    protected void registerExecutions() {
        registerToolExecution("billing.getInvoice", args ->
                billingUseCase.getInvoice(toolArgAs(args, "invoiceId", String.class)));

        registerToolExecution("billing.evaluate", args -> {
            BillingPayload payload = toolBodyAs(args, BillingPayload.class);
            return billingUseCase.evaluate(payload, toolBodyAsJson(args));
        });

        registerResourceExecution("billing://health", uri ->
                billingUseCase.get());

        registerResourceExecution("billing://health/by-region", uri ->
                billingUseCase.get(requiredResourceQueryParam(uri, "region")));

        registerResourceExecution("billing://health/by-region-and-status", uri ->
                billingUseCase.get(
                        requiredResourceQueryParam(uri, "region"),
                        requiredResourceQueryParam(uri, "status")
                ));
    }
}
```

En este esquema, cada `registerResourceExecution(...)` se hace con una URI de resource. El cliente MCP consume y lee esas mismas URIs.

Cuando se necesita enviar argumentos a un resource, se pueden incluir en el query string del URI. En este PoC hay tres casos separados:

- `openapi://summary` -> ejecución sin argumentos.
- `openapi://operations/searchOrders?customerDocument=DOC-100` -> ejecución con un argumento (`customerDocument`).
- `openapi://operations/evaluateCustomerProfile?region=eu-west-1&status=DOWN` -> ejecución con dos argumentos (`region`, `status`).

## Configuración relevante

Archivo: `src/main/resources/application.yml`

- `app.openapi.location`: ubicación del contrato OpenAPI.
- `app.overrides.location`: ubicación de overrides YAML (opcional).
- `app.manifest.export-on-startup`: exporta snapshots en startup (solo para uso local/debug).
- `app.manifest.export-dir`: directorio de salida para manifiestos exportados.
- `app.manifest.generated-location`: ubicación externa opcional para leer `GeneratedManifest` (si no se define, se genera desde OpenAPI).
- `app.manifest.effective-location`: ubicación externa opcional para leer `EffectiveManifest` (si no se define, se calcula con merge).
- `app.manifest.generated-file`: nombre del snapshot del manifiesto generado.
- `app.manifest.effective-file`: nombre del snapshot del manifiesto efectivo.
- `app.manifest.lock-file`: nombre del lock manifest.
- `app.manifest.lock-mode`: `off`, `verify` o `frozen`.
- `app.manifest.overrides-mode`: `off`, `strict` o `warn`.
- `spring.ai.mcp.server.streamable-http.mcp-endpoint`: endpoint MCP (`/mcp`).

Reglas prácticas:

- `lock-mode=off`: genera desde OpenAPI en cada inicio y no valida lock.
- `lock-mode=verify`: genera desde OpenAPI y falla si no coincide con `mcp.lock.yaml`.
- `lock-mode=frozen`: no relee OpenAPI; usa `mcp.generated.yaml` y valida lock.
- `generated-location` definido: intenta usar ese generated externo; si no existe o no es legible, genera desde OpenAPI.
- `effective-location` definido: intenta usar ese effective externo; si no existe o no es legible, calcula el merge.
- `overrides-mode=off`: desactiva overrides por seguridad.
- `overrides-mode=strict`: aplica overrides y falla si hay claves huérfanas.
- `overrides-mode=warn`: aplica overrides válidos y advierte los huérfanos.
- Si `app.overrides.location` no está definido (o no existe), la app continúa con overrides vacíos.

Modelo stateless recomendado:

- Generar `mcp.generated.yaml`, `mcp.effective.yaml` y `mcp.lock.yaml` en CI/build.
- Para generar lock por primera vez: `lock-mode=off` + `export-on-startup=true` en el job de build.
- Desplegar esos artefactos como inmutables con la imagen o config de despliegue.
- Mantener `export-on-startup=false` en pods para evitar escritura runtime.
- En producción usar `lock-mode=verify` (o `frozen` si no quieres regeneración runtime).

## Ejecución local

### Requisitos

- JDK 21.

### Comandos

En Linux/macOS:

```bash
./gradlew bootRun
```

En Windows:

```bat
gradlew.bat bootRun
```

Para pruebas:

```bash
./gradlew test
```

### Prueba de resources con parámetros en URI

1. Listar resources templates desde el cliente MCP (`resources/templates/list`).
2. Leer resources usando URI concretas (`resources/read`) como:
   - `openapi://summary`
   - `openapi://operations/searchOrders?customerDocument=DOC-100`
   - `openapi://operations/evaluateCustomerProfile?region=eu-west-1&status=DOWN`

El servidor usa esas URIs para resolver parámetros y ejecutar el caso de uso correspondiente.

## Estructura de archivos clave

- `src/main/resources/openapi/demo-contract.yaml`: contrato OpenAPI de ejemplo.
- `src/main/resources/mcp-overrides.yaml`: personalizaciones de tools/resources/prompts.
- `src/main/java/com/example/mcpdemo/config/DynamicMcpConfiguration.java`: ensamblado de beans dinámicos MCP.
- `src/main/java/com/example/mcpdemo/manifest/generator/OpenApiToManifestGenerator.java`: generación de manifiesto desde OpenAPI.
- `src/main/java/com/example/mcpdemo/manifest/merge/ManifestMerger.java`: fusión con overrides.
- `src/main/java/com/example/mcpdemo/runtime/DynamicToolCallback.java`: ejecución dinámica de herramientas.
- `src/main/java/com/example/mcpdemo/runtime/OperationInvokerRegistry.java`: contrato de resolución para tools y resources.
- `src/main/java/com/example/mcpdemo/runtime/binding/ManifestDrivenOperationInvokerRegistry.java`: resolución declarativa por `binding.mode` con fallback a registry clásico.
- `src/main/java/com/example/mcpdemo/runtime/binding/SpringBeanBindingModeInvokerFactory.java`: binding declarativo para beans Spring.
- `src/main/java/com/example/mcpdemo/runtime/binding/HttpBindingModeInvokerFactory.java`: binding declarativo para invocación HTTP.
- `src/main/java/com/example/mcpdemo/runtime/OperationExecutionContributor.java`: contrato contributor para registrar lógica ejecutable.
- `src/main/java/com/example/mcpdemo/runtime/AbstractOperationExecutionContributor.java`: base para registrar ejecuciones en forma concisa.
- `src/main/java/com/example/mcpdemo/runtime/CompositeOperationInvokerRegistry.java`: consolidación de múltiples contributors.
- `src/main/java/com/example/mcpdemo/runtime/SampleOperationExecutionContributor.java`: contributor de ejemplo opcional.
- `src/main/java/com/example/mcpdemo/runtime/SampleResourceExecutionContributor.java`: contributor de ejemplo opcional para resources dinámicos.
- `src/main/java/com/example/mcpdemo/runtime/OperationInvokerRegistryConfiguration.java`: composición del registry declarativo y contributors detectados en el contexto.
- `src/main/java/com/example/mcpdemo/sample/usecase/CustomerProfileEvaluationUseCase.java`: caso de uso de ejemplo para payloads JSON complejos.
- `src/main/java/com/example/mcpdemo/sample/usecase/CustomerStatusResourceUseCase.java`: caso de uso de resource que resuelve datos vía `get()`.
- `src/main/java/com/example/mcpdemo/sample/usecase/MockCustomerStatusClient.java`: cliente mock con operación `get()` para simular lectura externa.

## Alcance actual y límites del PoC

- El `inputSchema` y `outputSchema` se generan en formato simplificado para acelerar validación técnica.
- El modelado de `requestBody` es básico y no cubre todos los casos de OpenAPI.
- La resolución de plantillas de prompts usa reemplazo directo de placeholders.
- El modo `http` es intencionalmente básico (sin retries/circuit breaker/policies avanzadas).
