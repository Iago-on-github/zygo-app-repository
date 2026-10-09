package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.utils.StorageUrlMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring",uses = StorageUrlMapper.class)
public interface AdministratorResponseMapper {

    @Mapping(target = "email", source = "administrator.userAccount.email")
    @Mapping(target = "addressResponse", source = "administrator.address")
    @Mapping(target = "profilePicture", source = "profilePicture", qualifiedByName = "toPublicUrl")
    AdministratorResponseDTO toDTO(Administrator administrator);
}
