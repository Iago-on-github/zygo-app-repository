package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.dtos.request.AdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface AdministratorRequestMapper {

    @Mapping(target = "id", ignore = true)
    Administrator toEntity(AdministratorRequestDTO administratorRequestDTO);

    @Mapping(target = "userAccount.password", ignore = true)
    @Mapping(target = "userAccount.email", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void administratorUpdateFromDTO(AdministratorUpdateDTO admUpdateDTO, @MappingTarget Administrator admEntity);
}
