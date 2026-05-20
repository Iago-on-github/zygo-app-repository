package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface AdministratorMapper {

    @Mapping(target = "password", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void administratorUpdateFromDTO(AdministratorUpdateDTO admUpdateDTO, @MappingTarget Administrator admEntity);
}
