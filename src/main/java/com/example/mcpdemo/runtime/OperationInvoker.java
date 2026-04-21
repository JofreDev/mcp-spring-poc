package com.example.mcpdemo.runtime;

import java.util.Map;

@FunctionalInterface
public interface OperationInvoker {
    Object invoke(Map<String, Object> arguments);
}
