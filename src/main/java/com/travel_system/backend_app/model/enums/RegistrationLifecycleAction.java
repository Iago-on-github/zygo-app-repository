package com.travel_system.backend_app.model.enums;

/*
 * ação decidida para uma matrícula em uma execução do job (no máximo uma por dia)
 * */

public enum RegistrationLifecycleAction {
    NONE(null),
    CLOSE(null),
    BLOCK_WARNING(RegistrationReminderStage.BLOCK_WARNING),
    GRACE_STARTED(RegistrationReminderStage.GRACE_STARTED),
    EXPIRING_1(RegistrationReminderStage.DAYS_1),
    EXPIRING_7(RegistrationReminderStage.DAYS_7),
    EXPIRING_30(RegistrationReminderStage.DAYS_30),
    WINDOW_OPENED(RegistrationReminderStage.WINDOW_OPENED);

    // estágio gravado após o envio (null = ação sem estágio)
    private final RegistrationReminderStage stage;

    RegistrationLifecycleAction(RegistrationReminderStage stage) {
        this.stage = stage;
    }

    public RegistrationReminderStage getStage() {
        return stage;
    }
}
/*
* GUIDE
*
* NONE = nada se aplica
* CLOSE = fechamento
* BLOCK_WARNING = quantidade de dias para bloqueio
* GRACE_STARTED = quantidade de dias toleráveis para envio após fechamento do período
* EXPIRING_1, EXPIRING_7, EXPIRING_30 = respectivamente a quantidade de dias para envio de notificações estratégicas de lembrete
* WINDOW_OPENED =
* */
