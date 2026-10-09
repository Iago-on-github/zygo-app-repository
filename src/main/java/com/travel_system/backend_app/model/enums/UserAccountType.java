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
    UNASSIGNED; // usuário global sem tenant (estado padrão assim que criado a conta)

    public final String STUDENT_PERMISSION_ROLE = "ROLE_USER";
    public final String DRIVER_PERMISSION_ROLE = "ROLE_DRIVER";
    public final String ADMINISTRATOR_PERMISSION_ROLE = "ROLE_ADMIN";
    public final String PLATFORM_ADMINISTRATOR_PERMISSION_ROLE = "ROLE_PLATFORM_ADMIN";
    public final String RESPONSIBLE_ADULT_PERMISSION_ROLE = "ROLE_RESPONSIBLE_ADULT";
}
