package com.travel_system.backend_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel_system.backend_app.interfaces.SensitiveOperationExecutorStrategy;
import com.travel_system.backend_app.model.SensitiveOperation;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import org.bouncycastle.pqc.crypto.ExchangePair;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SensitiveOperationExecutionService {

    private final Map<SensitiveOperationType, SensitiveOperationExecutorStrategy> executors;

    public SensitiveOperationExecutionService(List<SensitiveOperationExecutorStrategy> executorList) {
        this.executors = executorList.stream()
                .collect(Collectors.toMap(SensitiveOperationExecutorStrategy::supports, e -> e));
    }

    public void execute(SensitiveOperation operation) {
        SensitiveOperationExecutorStrategy executor = executors.get(operation.getSensitiveOperationType());

        if (executor == null) {
            throw new IllegalStateException("Nenhum executor registrado para o tipo: " + operation.getSensitiveOperationType());
        }

        try {
            executor.execute(operation);
        } catch (Exception e) {
            throw new RuntimeException("Erro durante a criação do PlatformAdministrator");
        }
    }

}

/*
* GUIDE:
* Atua como um "roteador", verifica o "sensitiveOperationType" de uma operação e verifica qual executor sabe lidar com isso, delegando a operação para ele.
* */
