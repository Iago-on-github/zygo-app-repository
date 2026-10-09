package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.CustomerTerm;
import com.travel_system.backend_app.model.dtos.request.CustomerTermResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerTermResponseMapper {

    CustomerTermResponseDTO toDTO(CustomerTerm customerTerm);
}
