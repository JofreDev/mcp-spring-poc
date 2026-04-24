package com.example.mcpdemo.runtime.binding;

import com.example.mcpdemo.manifest.model.ResourceDescriptor;
import com.example.mcpdemo.manifest.model.ToolDescriptor;
import com.example.mcpdemo.runtime.OperationInvoker;
import com.example.mcpdemo.runtime.ResourceInvoker;

import java.util.Optional;

public interface BindingModeInvokerFactory {

    String mode();

    default Optional<OperationInvoker> createToolInvoker(ToolDescriptor tool) {
        return Optional.empty();
    }

    default Optional<ResourceInvoker> createResourceInvoker(ResourceDescriptor resource) {
        return Optional.empty();
    }
}
