package com.travel_system.backend_app.config.constants;

import java.util.concurrent.TimeUnit;

public class GlobalAppConstants {

    private GlobalAppConstants() {
        throw new UnsupportedOperationException("Não é possível instanciar uma classe de constantes");
    }

    // ## routestop monitoring
    public static final Double MONITORING_THRESHOLD = 4.0;
    public static final Double APPROACHING_THRESHOLD = 1.5;
    public static final Double REACHED_THRESHOLD = 50.0;

    // ## location
    public static final double AUTO_DISCONNECT_DISTANCE_METERS = 350;
    public static final long AUTO_DISCONNECT_TIME = TimeUnit.MINUTES.toMillis(5);

    // ## route
    public static final double TOLERANCE_DISTANCE = 50.0;
    public static final double EARTH_RADIUS_METERS = 6371000;
}
