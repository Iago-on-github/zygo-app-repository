package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.dtos.request.DriverUpdateDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface DriverMapper {

    @Mapping(target = "password", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void driverUpdateFromDTO(DriverUpdateDTO driverUpdateDTO, @MappingTarget Driver driverEntity);
}
