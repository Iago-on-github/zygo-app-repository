package com.travel_system.backend_app.config.constants;

import java.util.concurrent.TimeUnit;

public class GlobalAppConstants {

    public GlobalAppConstants() {
        throw new UnsupportedOperationException("Não é possível instanciar uma classe de constantes");
    }

    // ## routestop monitoring
    public static final double MONITORING_THRESHOLD = 4.0;
    public static final double APPROACHING_THRESHOLD = 1.5;
    public static final double REACHED_THRESHOLD = 50.0;

    // ## location
    public static final double AUTO_DISCONNECT_DISTANCE_METERS = 350.0;
    public static final double AUTO_CONNECTED_DISTANCE_METERS = 50.0;
    public static final long AUTO_DISCONNECT_TIME = TimeUnit.MINUTES.toMillis(5);

    // ## route
    public static final double TOLERANCE_DISTANCE = 50.0;
    public static final double EARTH_RADIUS_METERS = 6371000;

    public static final int TOTAL_ROUTE_STOP_POINTS_PER_STUDENT = 6;

    // limite de entidades cadastradas no sistema
    public static final int ADMINISTRATOR_RECORD_LIMIT = 2;
    public static final int DRIVER_RECORD_LIMIT = 4;
    public static final int STUDENT_RECORD_LIMIT = 50;
}
