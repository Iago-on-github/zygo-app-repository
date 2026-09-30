package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.dtos.invitation.driver.DriverProfileDTO;
import com.travel_system.backend_app.model.dtos.request.DriverRequestDTO;
import com.travel_system.backend_app.model.dtos.request.DriverUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DriverRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "userAccount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "travels", ignore = true)
    @Mapping(target = "totalTrips", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "name", source = "driverAccept.name")
    @Mapping(target = "lastName", source = "driverAccept.lastName")
    @Mapping(target = "telephone", source = "driverAccept.telephone")
    @Mapping(target = "birthdate", source = "driverAccept.birthdate")
    @Mapping(target = "areaOfActivity", source = "driverInvitation.areaOfActivity")
    @Mapping(target = "driverShifts", source = "driverInvitation.driverShifts")
    Driver toEntity(DriverProfileDTO profileDTO);

    @Mapping(target = "userAccount.password", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void driverUpdateFromDTO(DriverUpdateDTO driverUpdateDTO, @MappingTarget Driver driverEntity);

    void driverUpdateStatusFromDTO(UpdateEntityStatusDTO driverStatus, @MappingTarget Driver driverEntity);
}
