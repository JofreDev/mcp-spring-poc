package com.example.mcpdemo.runtime;

import com.example.mcpdemo.sample.usecase.CustomerStatusResourceUseCase;
/*

public class SampleResourceExecutionContributor extends AbstractOperationExecutionContributor {

    private final CustomerStatusResourceUseCase customerStatusResourceUseCase;

    public SampleResourceExecutionContributor(CustomerStatusResourceUseCase customerStatusResourceUseCase) {
        this.customerStatusResourceUseCase = customerStatusResourceUseCase;
    }

    @Override
    protected void registerExecutions() {
        registerResourceExecution("openapi://summary", customerStatusResourceUseCase::get);

        registerResourceExecution("openapi://operations/searchOrders", uri ->
                customerStatusResourceUseCase.get(uri, requiredResourceQueryParam(uri, "customerDocument"))
        );

        registerResourceExecution("openapi://operations/evaluateCustomerProfile", uri ->
                customerStatusResourceUseCase.get(
                        uri,
                        requiredResourceQueryParam(uri, "region"),
                        requiredResourceQueryParam(uri, "status")
                )
        );
    }
}
*/