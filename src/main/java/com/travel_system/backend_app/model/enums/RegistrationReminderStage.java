package com.travel_system.backend_app.model.enums;

/*
* serve como identificador do campo "lastReminderStage" para que nao fiquem números soltos no código e dificeis de identificar
* no banco, no entanto, ele ainda é gravado como inteiro
* */

public enum RegistrationReminderStage {
    NONE (1),
    WINDOW_OPENED (2),
    DAYS_30 (3),
    DAYS_7 (4),
    DAYS_1 (5),
    GRACE_STARTED (6),
    BLOCK_WARNING (7);

    private final int value;

    RegistrationReminderStage(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    // null no banco = nenhum aviso enviado
    private static int valueOf(Integer stage) {
        return stage == null ? NONE.value : stage;
    }
}
