package com.travel_system.backend_app.config.constants;

import java.util.concurrent.TimeUnit;

public class NotificationConstants {

    private NotificationConstants() {
        throw new UnsupportedOperationException("Não é possível instanciar uma classe de constantes");
    }

    public static final long INVALID_ROUTE_LAST_NOTIFY_TIME = TimeUnit.MINUTES.toMillis(5);


    // processVehicleMovement
    public static final long STATE_TIME_LIMIT_MS = 4_000;
    public static final long NOTIFICATION_COOLDOWN_MS = 12_000;
    public static final long NOTIFICATION_COOLDOWN_MS_STOPPED = 300_000;
}
