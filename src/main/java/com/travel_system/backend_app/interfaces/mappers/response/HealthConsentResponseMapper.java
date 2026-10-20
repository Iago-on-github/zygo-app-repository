package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.HealthConsent;
import com.travel_system.backend_app.model.dtos.response.HealthConsentResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface HealthConsentResponseMapper {

    HealthConsentResponseDTO toDTO(HealthConsent healthConsent);
}
