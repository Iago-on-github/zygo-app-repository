package com.travel_system.backend_app.config.constants;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class TravelConstants {

    public TravelConstants() {
        throw new UnsupportedOperationException("Não é possível instanciar uma classe de constantes");
    }

    // tempo respeitado desde o último ping para encerramento da viagem de forma automática
    public static final long TRIP_INACTIVITY_TIMEOUT = TimeUnit.MINUTES.toMillis(5);

    public static final long STUDENT_TIME_FRAME = Duration.ofMinutes(2).toMillis();
    public static final int COUNT_STUDENT_ENTER_TRIP = 4;

}
