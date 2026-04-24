package com.example.mcpdemo.manifest.model;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record BindingDescriptor(
        String handler,
        String mode,
        Map<String, Object> options
) {

    public static final String MODE_REGISTRY = "registry";

    public String effectiveMode() {
        if (mode == null || mode.isBlank()) {
            return MODE_REGISTRY;
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    public Map<String, Object> optionsOrEmpty() {
        return options != null ? options : Map.of();
    }

    public String optionAsString(String key) {
        Object value = optionsOrEmpty().get(key);
        return value != null ? String.valueOf(value) : null;
    }

    public List<String> optionAsStringList(String key) {
        Object value = optionsOrEmpty().get(key);
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }
        return listValue.stream().map(String::valueOf).toList();
    }

    public BindingDescriptor merge(BindingDescriptor override) {
        if (override == null) {
            return this;
        }

        String mergedHandler = override.handler != null ? override.handler : this.handler;
        String mergedMode = override.mode != null ? override.mode : this.mode;

        Map<String, Object> mergedOptions = new LinkedHashMap<>(optionsOrEmpty());
        if (override.options != null) {
            mergedOptions.putAll(override.options);
        }

        return new BindingDescriptor(mergedHandler, mergedMode, mergedOptions);
    }
}
