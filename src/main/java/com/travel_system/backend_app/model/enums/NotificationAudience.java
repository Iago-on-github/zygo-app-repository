package com.travel_system.backend_app.model.enums;

// Enum destinado a separar notificações com base nos usuários que devem recebe-la

public enum NotificationAudience {
    CUSTOMER_STUDENTS,
    CUSTOMER_DRIVERS,
    CUSTOMER_ADMINS,
    CUSTOMER_RESPONSIBLES,
    ALL_CUSTOMER_USERS,

    SPECIFIC_STUDENT,
    SPECIFIC_DRIVER,

    PERIOD_STUDENTS, // notificações com base no período

    TRAVEL_STUDENTS,
    TRAVEL_RESPONSIBLES,
    STUDENT_RESPONSIBLE,
    EMBARKED_TRAVEL_STUDENTS
}

/*
* GUIDE:
*
* CUSTOMER_STUDENTS (DRIVERS, ADMINS, RESPONSIBLES e ALL_USERS):
*   - notificam todos os usuários do customer em específico com base na sua entidade. Salvo o ALL USERS, que notifica literalmente todos do customer
*
* SPECIFIC_STUDENT (DRIVER):
*   - notifica o UserAccount vinculado a um Student ou Driver específico (via studentId/driverId)
*
* PERIOD_STUDENTS:
*   - notifica os estudantes de um periodo específico (morning, afternoon, night)
*
* TRAVEL_STUDENTS:
*   - notifica todos os estudantes em uma viagem (pode não ter embarcado ainda, mas já está conectado à viagem)
*
* TRAVEL_RESPONSIBLES:
*   - notifica os responsáveis de todos os estudantes vinculados a uma viagem específica
*
* STUDENT_RESPONSIBLE:
*   - notifica apenas o responsável (ResponsibleAdult) vinculado a um estudante específico (via studentId)
*
* EMBARKED_TRAVEL_STUDENTS:
*   - notifica apenas os estudantes embarcados no viagem em específico
*
* */