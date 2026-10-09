package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.utils.StorageUrlMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = StorageUrlMapper.class)
public interface DriverResponseMapper {

    @Mapping(target = "email", source = "driver.userAccount.email")
    @Mapping(target = "addressResponse", source = "driver.address")
    @Mapping(target = "cnhResponse", source = "driver.cnh")
    @Mapping(target = "profilePicture", source = "profilePicture", qualifiedByName = "toPublicUrl")
    DriverResponseDTO toDTO(Driver driver);

}
