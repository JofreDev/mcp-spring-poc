package com.example.mcpdemo.runtime;

@FunctionalInterface
public interface OperationExecutionContributor {

    void contribute(OperationExecutionRegistrar registrar);
}
