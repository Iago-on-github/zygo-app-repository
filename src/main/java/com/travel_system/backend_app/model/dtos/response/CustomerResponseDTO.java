package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.enums.ClientSector;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.CityRepository;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponseDTO(UUID id,
                                  String name,
                                  String slug,
                                  String cnpj,
                                  GeneralStatus status,
                                  CityResponseDTO city,
                                  ClientSector clientSector,
                                  String profilePicture,
                                  Instant createdAt) {
}
