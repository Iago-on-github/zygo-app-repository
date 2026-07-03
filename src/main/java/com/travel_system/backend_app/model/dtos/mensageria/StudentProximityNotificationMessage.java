package com.travel_system.backend_app.model.dtos.mensageria;

import java.util.UUID;

public record StudentProximityNotificationMessage(UUID travelId,
                                                  UUID studentId,
                                                  Double distance,
                                                  String zone,
                                                  String timestamp,
                                                  String alertType) {
}
