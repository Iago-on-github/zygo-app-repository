package com.travel_system.backend_app.config.constants;

public class CacheConstants {

    private CacheConstants() {
        throw new UnsupportedOperationException("Não é possível instanciar uma classe de constantes");
    }

    public static final String STUDENT_TRAVEL_ROUTE_STOPS_KEY = "travel:student:route-stops:";

    // ## redis notification
    public static final String HASH_KEY_PREFIX = "notification:";

    // ## redis tracking
    public static final String SET_KEY = "ACTIVE_TRAVELS_KEY";

    public static final String TRACKING_KEY_PREFIX = "travel:tracking:";
    public static final String ROUTE_KEY_PREFIX = "travel:route:";
    public static final String STUDENT_TRAVEL_KEY_PREFIX = "travel:away_students:";
    public static final String STUDENT_AWAY_STATE_LOCK = "travel:student-away-lock:";
    public static final String STUDENT_ROUTE_STOP_MONITORING = "student:route-stop-monitoring:";

    // travel static cache
    public static final String TRAVEL_STATIC_CACHE = "trip:static:";
}
