package com.travel_system.backend_app.exceptions;

import com.travel_system.backend_app.model.enums.RegistrationMissingItem;

import java.util.List;

/*
 * solicitação incompleta: carrega TODOS os itens que faltam (400)
 * */
public class RegistrationIncompleteException extends RuntimeException {

    private final List<RegistrationMissingItem> missingItems;

    public RegistrationIncompleteException(List<RegistrationMissingItem> missingItems) {
        super("A solicitação de matrícula está incompleta:" + missingItems);
        this.missingItems = List.copyOf(missingItems);
    }

    public List<RegistrationMissingItem> getMissingItems() {
        return missingItems;
    }
}
