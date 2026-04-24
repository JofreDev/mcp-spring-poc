package com.example.mcpdemo.runtime;

import com.example.mcpdemo.sample.usecase.CustomerProfileEvaluationUseCase;
import com.example.mcpdemo.sample.usecase.CustomerQueryUseCase;
import com.example.mcpdemo.sample.usecase.OrderQueryUseCase;
/*
public class SampleOperationExecutionContributor extends AbstractOperationExecutionContributor {

    private final CustomerProfileEvaluationUseCase customerProfileEvaluationUseCase;
    private final CustomerQueryUseCase customerQueryUseCase;
    private final OrderQueryUseCase orderQueryUseCase;

    public SampleOperationExecutionContributor(CustomerQueryUseCase customerQueryUseCase,
                                               OrderQueryUseCase orderQueryUseCase,
                                               CustomerProfileEvaluationUseCase customerProfileEvaluationUseCase) {
        this.customerQueryUseCase = customerQueryUseCase;
        this.orderQueryUseCase = orderQueryUseCase;
        this.customerProfileEvaluationUseCase = customerProfileEvaluationUseCase;
    }

    @Override
    protected void registerExecutions() {
        OperationInvoker customerById =
                args -> customerQueryUseCase.getById(toolArgAs(args, "customerId", String.class));
        OperationInvoker ordersSearch =
                args -> orderQueryUseCase.search(
                        optionalToolArgAs(args, "customerDocument", String.class),
                        optionalToolArgAs(args, "status", String.class)
                );
        OperationInvoker evaluateCustomerProfile =
                args -> customerProfileEvaluationUseCase.evaluate(
                        toolBodyAs(args, CustomerProfileEvaluationUseCase.ProfileEvaluationRequest.class),
                        toolBodyAsJson(args)
                );

        registerToolExecution("customers.getById", customerById);
        registerToolExecution("orders.search", ordersSearch);
        registerToolExecution("customers.evaluateProfile", evaluateCustomerProfile);

        registerToolExecution("getCustomerById", customerById);
        registerToolExecution("searchOrders", ordersSearch);
        registerToolExecution("evaluateCustomerProfile", evaluateCustomerProfile);

    }
}
*/