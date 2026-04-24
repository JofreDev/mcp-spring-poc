package com.example.mcpdemo.runtime.binding;

import com.example.mcpdemo.manifest.model.BindingDescriptor;
import com.example.mcpdemo.manifest.model.ResourceDescriptor;
import com.example.mcpdemo.manifest.model.ToolDescriptor;
import com.example.mcpdemo.runtime.OperationInvoker;
import com.example.mcpdemo.runtime.ResourceInvoker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class SpringBeanBindingModeInvokerFactory implements BindingModeInvokerFactory {

    private static final String MODE = "spring-bean";

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    public SpringBeanBindingModeInvokerFactory(ApplicationContext applicationContext,
                                               ObjectMapper objectMapper) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
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

        String context = "tool '" + tool.name() + "'";
        MethodInvocationPlan plan = buildPlan(binding, context);
        return Optional.of(args -> plan.invoke(args != null ? args : Map.of()));
    }

    @Override
    public Optional<ResourceInvoker> createResourceInvoker(ResourceDescriptor resource) {
        BindingDescriptor binding = resource != null ? resource.binding() : null;
        if (binding == null || !MODE.equals(binding.effectiveMode())) {
            return Optional.empty();
        }

        String context = "resource '" + resource.uri() + "'";
        MethodInvocationPlan plan = buildPlan(binding, context);
        return Optional.of(requestedUri -> {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("_uri", requestedUri);
            source.putAll(resourceQueryParams(requestedUri));
            return plan.invoke(source);
        });
    }

    private MethodInvocationPlan buildPlan(BindingDescriptor binding, String context) {
        Map<String, Object> options = binding.optionsOrEmpty();
        String beanName = optionAsString(options, "bean");
        String methodName = optionAsString(options, "method");

        if ((beanName == null || beanName.isBlank()) || (methodName == null || methodName.isBlank())) {
            BeanMethodTarget targetFromHandler = parseHandlerTarget(binding.handler(), context);
            beanName = beanName != null && !beanName.isBlank() ? beanName : targetFromHandler.beanName();
            methodName = methodName != null && !methodName.isBlank() ? methodName : targetFromHandler.methodName();
        }

        if (beanName == null || beanName.isBlank()) {
            throw new IllegalStateException("Missing binding options.bean for " + context);
        }
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalStateException("Missing binding options.method for " + context);
        }

        Object bean = resolveBean(beanName, context);
        List<String> argsOrder = binding.optionAsStringList("args");
        Method method = selectMethod(bean.getClass(), methodName, argsOrder, context);
        return new MethodInvocationPlan(bean, method, argsOrder);
    }

    private Object resolveBean(String beanName, String context) {
        if (!applicationContext.containsBean(beanName)) {
            throw new IllegalStateException("Bean '" + beanName + "' not found for " + context);
        }
        return applicationContext.getBean(beanName);
    }

    private Method selectMethod(Class<?> beanType,
                                String methodName,
                                List<String> argsOrder,
                                String context) {
        List<Method> candidates = new ArrayList<>();
        for (Method method : beanType.getMethods()) {
            if (method.getName().equals(methodName)) {
                candidates.add(method);
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("Method '" + methodName + "' not found on bean type "
                    + beanType.getName() + " for " + context);
        }

        if (!argsOrder.isEmpty()) {
            candidates = candidates.stream()
                    .filter(method -> method.getParameterCount() == argsOrder.size())
                    .toList();
            if (candidates.isEmpty()) {
                throw new IllegalStateException("No method overload matches args size " + argsOrder.size()
                        + " for method '" + methodName + "' on " + beanType.getName() + " in " + context);
            }
        }

        if (candidates.size() > 1) {
            String signatures = candidates.stream()
                    .map(this::methodSignature)
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Ambiguous method mapping for " + context + ": " + signatures
                    + ". Define binding.options.args to disambiguate");
        }

        return candidates.getFirst();
    }

    private String methodSignature(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
        return method.getName() + "(" + parameters + ")";
    }

    private String optionAsString(Map<String, Object> options, String key) {
        Object value = options.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private BeanMethodTarget parseHandlerTarget(String handler, String context) {
        if (handler == null || handler.isBlank()) {
            throw new IllegalStateException("Missing handler and missing binding options.bean/method for " + context);
        }

        String expression = handler.trim();
        int delimiter = expression.indexOf('#');
        if (delimiter <= 0 || delimiter >= expression.length() - 1) {
            throw new IllegalStateException("Invalid handler expression for " + context + ": " + handler
                    + ". Expected '<bean>#<method>'");
        }

        return new BeanMethodTarget(
                expression.substring(0, delimiter),
                expression.substring(delimiter + 1)
        );
    }

    private Map<String, String> resourceQueryParams(String resourceUri) {
        URI uri = URI.create(Objects.requireNonNull(resourceUri, "resourceUri cannot be null"));
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }

        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            if (pair == null || pair.isBlank()) {
                continue;
            }

            int delimiterIndex = pair.indexOf('=');
            String rawKey = delimiterIndex >= 0 ? pair.substring(0, delimiterIndex) : pair;
            String rawValue = delimiterIndex >= 0 ? pair.substring(delimiterIndex + 1) : "";

            String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
            String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
            params.putIfAbsent(key, value);
        }
        return params;
    }

    private final class MethodInvocationPlan {

        private final Object bean;
        private final Method method;
        private final List<String> argsOrder;

        private MethodInvocationPlan(Object bean, Method method, List<String> argsOrder) {
            this.bean = bean;
            this.method = method;
            this.argsOrder = argsOrder;
        }

        private Object invoke(Map<String, Object> source) {
            Object[] arguments = resolveArguments(source != null ? source : Map.of());
            try {
                return method.invoke(bean, arguments);
            }
            catch (Exception e) {
                throw new IllegalStateException("Error invoking method '" + method.getName()
                        + "' on bean '" + bean.getClass().getName() + "'", e);
            }
        }

        private Object[] resolveArguments(Map<String, Object> source) {
            Parameter[] parameters = method.getParameters();
            if (parameters.length == 0) {
                return new Object[0];
            }

            if (!argsOrder.isEmpty()) {
                if (parameters.length != argsOrder.size()) {
                    throw new IllegalStateException("Configured args size " + argsOrder.size()
                            + " does not match method parameters " + parameters.length
                            + " for method '" + method.getName() + "'");
                }

                Object[] ordered = new Object[parameters.length];
                for (int index = 0; index < parameters.length; index++) {
                    String argKey = argsOrder.get(index);
                    ordered[index] = convertValue(resolveSourceValue(source, argKey), parameters[index].getType(), argKey);
                }
                return ordered;
            }

            if (parameters.length == 1) {
                Parameter parameter = parameters[0];
                if (Map.class.isAssignableFrom(parameter.getType())) {
                    return new Object[]{source};
                }

                String parameterName = parameter.getName();
                if (parameter.isNamePresent() && source.containsKey(parameterName)) {
                    return new Object[]{convertValue(source.get(parameterName), parameter.getType(), parameterName)};
                }

                if (source.containsKey("body")) {
                    return new Object[]{convertValue(source.get("body"), parameter.getType(), "body")};
                }

                List<Map.Entry<String, Object>> nonInternal = source.entrySet().stream()
                        .filter(entry -> !entry.getKey().startsWith("_"))
                        .toList();
                if (nonInternal.size() == 1) {
                    Map.Entry<String, Object> singleEntry = nonInternal.getFirst();
                    return new Object[]{convertValue(singleEntry.getValue(), parameter.getType(), singleEntry.getKey())};
                }

                throw new IllegalStateException("Cannot infer arguments for method '" + method.getName()
                        + "'. Configure binding.options.args");
            }

            Object[] inferred = new Object[parameters.length];
            for (int index = 0; index < parameters.length; index++) {
                Parameter parameter = parameters[index];
                if (!parameter.isNamePresent()) {
                    throw new IllegalStateException("Cannot infer argument for parameter #" + index
                            + " in method '" + method.getName() + "'. Compile with -parameters or configure binding.options.args");
                }
                String parameterName = parameter.getName();
                if (!source.containsKey(parameterName)) {
                    throw new IllegalStateException("Missing source value for parameter '" + parameterName
                            + "' in method '" + method.getName() + "'. Configure binding.options.args");
                }
                inferred[index] = convertValue(source.get(parameterName), parameter.getType(), parameterName);
            }
            return inferred;
        }

        private Object resolveSourceValue(Map<String, Object> source, String argKey) {
            String key = Objects.requireNonNull(argKey, "binding arg key cannot be null").trim();
            if (key.isBlank()) {
                throw new IllegalStateException("binding arg key cannot be blank");
            }

            if ("_args".equals(key)) {
                return source;
            }
            if (key.startsWith("literal:")) {
                return key.substring("literal:".length());
            }
            if (!source.containsKey(key)) {
                throw new IllegalStateException("Missing binding argument source: '" + key + "'");
            }
            return source.get(key);
        }

        private Object convertValue(Object value, Class<?> targetType, String sourceKey) {
            if (value == null) {
                if (targetType.isPrimitive()) {
                    throw new IllegalStateException("Null value for primitive parameter sourced from '" + sourceKey + "'");
                }
                return null;
            }

            if (targetType.isInstance(value)) {
                return value;
            }

            if (targetType == String.class) {
                return String.valueOf(value);
            }

            try {
                return objectMapper.convertValue(value, targetType);
            }
            catch (IllegalArgumentException e) {
                throw new IllegalStateException("Cannot convert source '" + sourceKey + "' to " + targetType.getName(), e);
            }
        }
    }

    private record BeanMethodTarget(String beanName, String methodName) {
    }
}
