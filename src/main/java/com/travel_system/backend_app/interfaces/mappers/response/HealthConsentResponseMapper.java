package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.HealthConsent;
import com.travel_system.backend_app.model.HealthDeclaration;
import com.travel_system.backend_app.model.dtos.response.HealthConsentResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface HealthConsentResponseMapper {

    @Mapping(target = "ownerId", source = "healthConsent", qualifiedByName = "resolveOwnerId")
    HealthConsentResponseDTO toDTO(HealthConsent healthConsent);

    @Named("resolveOwnerId")
    default UUID resolveOwnerId(HealthConsent healthConsent) {
        return healthConsent.getStudentId() != null ? healthConsent.getStudentId() : healthConsent.getDriverId();
    }
}
