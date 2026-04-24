package com.example.mcpdemo.runtime;

import com.example.mcpdemo.manifest.registry.ManifestRegistry;
import com.example.mcpdemo.runtime.binding.BindingModeInvokerFactory;
import com.example.mcpdemo.runtime.binding.HttpBindingModeInvokerFactory;
import com.example.mcpdemo.runtime.binding.ManifestDrivenOperationInvokerRegistry;
import com.example.mcpdemo.runtime.binding.SpringBeanBindingModeInvokerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OperationInvokerRegistryConfiguration {

    @Bean
    @ConditionalOnMissingBean(OperationInvokerRegistry.class)
    OperationInvokerRegistry operationInvokerRegistry(ManifestRegistry manifestRegistry,
                                                      ObjectProvider<OperationExecutionContributor> contributorsProvider,
                                                      ObjectProvider<BindingModeInvokerFactory> invokerFactoriesProvider) {
        List<OperationExecutionContributor> contributors = contributorsProvider.orderedStream().toList();
        CompositeOperationInvokerRegistry contributorOperationInvokerRegistry = new CompositeOperationInvokerRegistry(contributors);

        List<BindingModeInvokerFactory> invokerFactories = invokerFactoriesProvider.orderedStream().toList();
        return new ManifestDrivenOperationInvokerRegistry(
                manifestRegistry,
                contributorOperationInvokerRegistry,
                invokerFactories
        );
    }

    @Bean
    @ConditionalOnMissingBean(SpringBeanBindingModeInvokerFactory.class)
    BindingModeInvokerFactory springBeanBindingModeInvokerFactory(ApplicationContext applicationContext,
                                                                  ObjectMapper objectMapper) {
        return new SpringBeanBindingModeInvokerFactory(applicationContext, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(HttpBindingModeInvokerFactory.class)
    BindingModeInvokerFactory httpBindingModeInvokerFactory(ObjectMapper objectMapper) {
        return new HttpBindingModeInvokerFactory(objectMapper);
    }
}
