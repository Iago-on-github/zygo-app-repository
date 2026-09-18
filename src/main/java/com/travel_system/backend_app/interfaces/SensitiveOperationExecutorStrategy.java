package com.travel_system.backend_app.interfaces;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel_system.backend_app.model.SensitiveOperation;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;

public interface SensitiveOperationExecutorStrategy {
    SensitiveOperationType supports();

    void execute(SensitiveOperation operation) throws JsonProcessingException;
}

/*
* supports é o tipo específico da Operação (ex.: criação)
* execute é onde o payload é desserializado e efetivamente ocorre a criação do novo platform adm
* */
