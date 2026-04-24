package com.example.mcpdemo.runtime;

import java.util.Map;

@FunctionalInterface
public interface OperationInvoker {
    // Hace algo con x argumentos
    Object invoke(Map<String, Object> arguments);
}
