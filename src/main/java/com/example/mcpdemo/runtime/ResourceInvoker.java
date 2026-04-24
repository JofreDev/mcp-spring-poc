package com.example.mcpdemo.runtime;

@FunctionalInterface
public interface ResourceInvoker {
    Object invoke(String resourceUri);
}
