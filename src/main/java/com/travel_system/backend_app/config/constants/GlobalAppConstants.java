package com.travel_system.backend_app.config.constants;

public class GlobalAppConstants {

    private GlobalAppConstants() {
        throw new UnsupportedOperationException("Não é possível instanciar uma classe de constantes");
    }

    public static final Double MONITORING_THRESHOLD = 4.0;
    public static final Double APPROACHING_THRESHOLD = 1.5;
    public static final Double REACHED_THRESHOLD = 50.0;
}
