package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.dtos.request.DriverRequestDTO;
import com.travel_system.backend_app.model.dtos.request.DriverUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface DriverRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "driverShifts", ignore = true)
    Driver toEntity(DriverRequestDTO driverRequestDTO);

    @Mapping(target = "userAccount.password", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void driverUpdateFromDTO(DriverUpdateDTO driverUpdateDTO, @MappingTarget Driver driverEntity);

    void driverUpdateStatusFromDTO(UpdateEntityStatusDTO driverStatus, @MappingTarget Driver driverEntity);
}
