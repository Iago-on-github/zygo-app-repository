package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record VehicleLocationRequestDTO(UUID travelId,
                                        @NotNull Double latitude,
                                        @NotNull Double longitude,
                                        Double speed,
                                        Double heading) {
}
