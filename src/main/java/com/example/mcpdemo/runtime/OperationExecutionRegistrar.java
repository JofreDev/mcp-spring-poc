package com.example.mcpdemo.runtime;

public interface OperationExecutionRegistrar {

    void registerToolExecution(String toolName, OperationInvoker execution);

    void registerResourceExecution(String resourceUri, ResourceInvoker execution);
}
