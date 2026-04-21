package com.example.mcpdemo.runtime;

import com.example.mcpdemo.sample.usecase.CustomerQueryUseCase;
import com.example.mcpdemo.sample.usecase.OrderQueryUseCase;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OperationInvokerRegistry {

    private final Map<String, OperationInvoker> invokers = new ConcurrentHashMap<>();

    public OperationInvokerRegistry(CustomerQueryUseCase customerQueryUseCase,
                                    OrderQueryUseCase orderQueryUseCase) {

        invokers.put("customers.getById", args -> customerQueryUseCase.getById((String) args.get("customerId")));
        invokers.put("orders.search", args -> orderQueryUseCase.search(
                (String) args.get("customerDocument"),
                (String) args.get("status")
        ));

        // fallback si el contrato usa operationId como handler
        invokers.put("getCustomerById", args -> customerQueryUseCase.getById((String) args.get("customerId")));
        invokers.put("searchOrders", args -> orderQueryUseCase.search(
                (String) args.get("customerDocument"),
                (String) args.get("status")
        ));
    }

    public OperationInvoker required(String handler) {
        OperationInvoker invoker = invokers.get(handler);
        if (invoker == null) {
            throw new IllegalArgumentException("No invoker registered for handler: " + handler);
        }
        return invoker;
    }


}
