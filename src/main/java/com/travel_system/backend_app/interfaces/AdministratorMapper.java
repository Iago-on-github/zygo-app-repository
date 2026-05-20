package com.travel_system.backend_app.interfaces;

import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface AdministratorMapper {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void administratorUpdateFromDTO(AdministratorUpdateDTO admUpdateDTO, @MappingTarget Administrator admEntity);
}
