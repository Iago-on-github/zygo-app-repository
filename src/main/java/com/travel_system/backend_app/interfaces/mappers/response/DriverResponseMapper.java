package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.dtos.request.DriverRequestDTO;
import com.travel_system.backend_app.model.dtos.response.CustomerResponseDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DriverResponseMapper {

    @Mapping(target = "email", source = "driver.userAccount.email")
    DriverResponseDTO toDTO(Driver driver);

}
