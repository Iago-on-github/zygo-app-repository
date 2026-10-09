package com.travel_system.backend_app.service.strategies.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.exceptions.CustomerNotFoundException;
import com.travel_system.backend_app.exceptions.PayloadNotFoundException;
import com.travel_system.backend_app.interfaces.SensitiveOperationExecutorStrategy;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.SensitiveOperation;
import com.travel_system.backend_app.model.dtos.request.ChangeCustomerPlanPayload;
import com.travel_system.backend_app.model.enums.CustomerPlan;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import com.travel_system.backend_app.repository.CustomerRepository;
import org.springframework.stereotype.Component;

@Component
public class ChangeCustomerPlanExecutor implements SensitiveOperationExecutorStrategy {

    private final CustomerRepository customerRepository;

    private final ObjectMapper objectMapper;

    public ChangeCustomerPlanExecutor(CustomerRepository customerRepository, ObjectMapper objectMapper) {
        this.customerRepository = customerRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public SensitiveOperationType supports() {
        return SensitiveOperationType.CHANGE_CUSTOMER_PLAN;
    }

    @Override
    public void execute(SensitiveOperation operation) throws JsonProcessingException {
        if (operation.getPayload() == null) {
            throw new PayloadNotFoundException("Payload de atualização do plano não encontrado para a operação sensível: " + operation.getId());
        }

        String payload = operation.getPayload();
        ChangeCustomerPlanPayload changeCustomerPlanPayload;
        try {
            changeCustomerPlanPayload = objectMapper.readValue(payload, ChangeCustomerPlanPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Ocorreu um erro duante a mudança de plano para o Customer");
        }

        Customer customer = customerRepository.findByCnpj(changeCustomerPlanPayload.cnpj())
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));

        CustomerPlan newCustomerPlan = changeCustomerPlanPayload.newPlan();

        customer.setPlan(newCustomerPlan);

        // manda email de notificação para o customer

        customerRepository.save(customer);
    }
}
