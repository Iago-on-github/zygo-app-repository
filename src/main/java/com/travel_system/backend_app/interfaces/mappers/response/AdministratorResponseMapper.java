package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdministratorResponseMapper {

    @Mapping(target = "email", source = "administrator.userAccount.email")
    AdministratorResponseDTO toDTO(Administrator administrator);
}
