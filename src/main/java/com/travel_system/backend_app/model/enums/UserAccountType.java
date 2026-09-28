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

    public final String STUDENT_PERMISSION_ROLE = "ROLE_ROLE";
    public final String DRIVER_PERMISSION_ROLE = "DRIVER_ROLE";
    public final String ADMINISTRATOR_PERMISSION_ROLE = "ADMINISTRATOR_ROLE";
    public final String PLATFORM_ADMINISTRATOR_PERMISSION_ROLE = "PLATFORM_ADMINISTRATOR_ROLE";
    public final String RESPONSIBLE_ADULT_PERMISSION_ROLE = "RESPONSIBLE_ADULT_ROLE";
}
