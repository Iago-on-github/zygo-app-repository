package com.travel_system.backend_app.model.enums;

/*
* ajuda a definir o tipo explicíto da conta
* */

public enum UserAccountType {
    STUDENT,
    DRIVER,
    ADMINISTRATOR,
    PLATFORM_ADMINISTRATOR,
    RESPONSIBLE_ADULT,
    UNASSIGNED // usuário global sem tenant (estado padrão assim que criado a conta)
}
