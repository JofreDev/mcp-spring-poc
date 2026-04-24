package com.example.mcpdemo.runtime.binding;

import com.example.mcpdemo.manifest.model.BindingDescriptor;
import com.example.mcpdemo.manifest.model.ResourceDescriptor;
import com.example.mcpdemo.manifest.model.ToolDescriptor;
import com.example.mcpdemo.runtime.OperationInvoker;
import com.example.mcpdemo.runtime.ResourceInvoker;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class HttpBindingModeInvokerFactory implements BindingModeInvokerFactory {

    private static final String MODE = "http";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpBindingModeInvokerFactory(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Override
    public String mode() {
        return MODE;
    }

    @Override
    public Optional<OperationInvoker> createToolInvoker(ToolDescriptor tool) {
        BindingDescriptor binding = tool != null ? tool.binding() : null;
        if (binding == null || !MODE.equals(binding.effectiveMode())) {
            return Optional.empty();
        }

        HttpBindingOptions options = HttpBindingOptions.from(binding, "tool '" + tool.name() + "'");
        return Optional.of(args -> executeHttp(options, args != null ? args : Map.of()));
    }

    @Override
    public Optional<ResourceInvoker> createResourceInvoker(ResourceDescriptor resource) {
        BindingDescriptor binding = resource != null ? resource.binding() : null;
        if (binding == null || !MODE.equals(binding.effectiveMode())) {
            return Optional.empty();
        }

        HttpBindingOptions options = HttpBindingOptions.from(binding, "resource '" + resource.uri() + "'");
        return Optional.of(uri -> executeHttp(options, resourceSource(uri)));
    }

    private Object executeHttp(HttpBindingOptions options, Map<String, Object> source) {
        URI requestUri = buildUri(options.url(), source, options.queryArgs());
        String method = options.method();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(requestUri)
                .timeout(options.timeout());

        options.headers().forEach(requestBuilder::header);

        if (hasBody(method)) {
            Object payload = options.bodyArg() != null ? source.get(options.bodyArg()) : source;
            String body = serializePayload(payload);
            requestBuilder.header("Content-Type", "application/json");
            requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        else {
            requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        try {
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP binding returned status " + response.statusCode() + " for "
                        + requestUri + ": " + response.body());
            }

            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                return "";
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (contentType.toLowerCase().contains("application/json")) {
                return objectMapper.readValue(responseBody, Object.class);
            }
            return responseBody;
        }
        catch (IOException e) {
            throw new IllegalStateException("Error reading HTTP response from " + requestUri, e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request interrupted for " + requestUri, e);
        }
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload != null ? payload : Map.of());
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to serialize HTTP binding payload", e);
        }
    }

    private boolean hasBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private URI buildUri(String baseUrl, Map<String, Object> source, List<String> queryArgs) {
        Map<String, Object> queryValues = new LinkedHashMap<>();
        if (!queryArgs.isEmpty()) {
            for (String key : queryArgs) {
                if (source.containsKey(key)) {
                    queryValues.put(key, source.get(key));
                }
            }
        }
        else {
            source.forEach((key, value) -> {
                if (!key.startsWith("_")) {
                    queryValues.put(key, value);
                }
            });
        }

        String query = queryValues.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> encode(entry.getKey()) + "=" + encode(String.valueOf(entry.getValue())))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");

        if (query.isBlank()) {
            return URI.create(baseUrl);
        }

        String separator = baseUrl.contains("?") ? "&" : "?";
        return URI.create(baseUrl + separator + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, Object> resourceSource(String uri) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("_uri", uri);

        URI parsed = URI.create(uri);
        String rawQuery = parsed.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return source;
        }

        for (String pair : rawQuery.split("&")) {
            if (pair == null || pair.isBlank()) {
                continue;
            }

            int delimiterIndex = pair.indexOf('=');
            String rawKey = delimiterIndex >= 0 ? pair.substring(0, delimiterIndex) : pair;
            String rawValue = delimiterIndex >= 0 ? pair.substring(delimiterIndex + 1) : "";

            String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
            String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
            source.putIfAbsent(key, value);
        }

        return source;
    }

    private record HttpBindingOptions(String url,
                                      String method,
                                      String bodyArg,
                                      List<String> queryArgs,
                                      Map<String, String> headers,
                                      Duration timeout) {

        private static HttpBindingOptions from(BindingDescriptor binding, String context) {
            Map<String, Object> options = binding.optionsOrEmpty();

            Object rawUrl = options.get("url");
            if (rawUrl == null || String.valueOf(rawUrl).isBlank()) {
                throw new IllegalStateException("Missing binding options.url for " + context);
            }

            String method = String.valueOf(options.getOrDefault("method", "POST")).trim().toUpperCase();
            String bodyArg = options.containsKey("bodyArg") ? String.valueOf(options.get("bodyArg")) : null;
            List<String> queryArgs = toStringList(options.get("queryArgs"));
            Map<String, String> headers = toStringMap(options.get("headers"));
            Duration timeout = parseTimeout(options.get("timeoutMs"));

            return new HttpBindingOptions(
                    String.valueOf(rawUrl),
                    method,
                    bodyArg,
                    queryArgs,
                    headers,
                    timeout
            );
        }

        private static List<String> toStringList(Object rawValue) {
            if (!(rawValue instanceof List<?> listValue)) {
                return List.of();
            }

            List<String> values = new ArrayList<>();
            for (Object value : listValue) {
                if (value != null) {
                    values.add(String.valueOf(value));
                }
            }
            return values;
        }

        private static Map<String, String> toStringMap(Object rawValue) {
            if (!(rawValue instanceof Map<?, ?> mapValue)) {
                return Map.of();
            }

            Map<String, String> values = new LinkedHashMap<>();
            mapValue.forEach((key, value) -> {
                if (key != null && value != null) {
                    values.put(String.valueOf(key), String.valueOf(value));
                }
            });
            return values;
        }

        private static Duration parseTimeout(Object rawTimeoutMs) {
            if (rawTimeoutMs == null) {
                return Duration.ofSeconds(10);
            }

            try {
                long timeoutMs = Long.parseLong(String.valueOf(rawTimeoutMs));
                if (timeoutMs <= 0) {
                    throw new IllegalStateException("binding options.timeoutMs must be greater than zero");
                }
                return Duration.ofMillis(timeoutMs);
            }
            catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid binding options.timeoutMs value: " + rawTimeoutMs, e);
            }
        }
    }
}
