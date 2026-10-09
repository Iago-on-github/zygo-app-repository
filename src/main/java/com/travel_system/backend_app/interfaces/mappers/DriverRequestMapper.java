package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Address;
import com.travel_system.backend_app.model.Cnh;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.dtos.invitation.driver.DriverProfileDTO;
import com.travel_system.backend_app.model.dtos.request.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DriverRequestMapper {


    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "cnh", source = "driverAccept.cnhRequest")
    @Mapping(target = "address", source = "driverAccept.addressRequest")
    @Mapping(target = "name", source = "driverAccept.name")
    @Mapping(target = "cpf", source = "driverAccept.cpf")
    @Mapping(target = "lastName", source = "driverAccept.lastName")
    @Mapping(target = "telephone", source = "driverAccept.telephone")
    @Mapping(target = "birthdate", source = "driverAccept.birthdate")
    @Mapping(target = "areaOfActivity", source = "driverInvitation.areaOfActivity")
    @Mapping(target = "driverShifts", source = "driverInvitation.driverShifts")
    Driver toEntity(DriverProfileDTO profileDTO);

    @Mapping(target = "name", source = "name")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "telephone", source = "telephone")
    @Mapping(target = "birthdate", source = "birthdate")
    @Mapping(target = "areaOfActivity", source = "areaOfActivity")
    @Mapping(target = "cpf", source = "cpf")
    @Mapping(target = "address", source = "addressUpdate")
    @Mapping(target = "cnh", source = "cnhUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, ignoreByDefault = true)
    void driverUpdateFromDTO(DriverUpdateDTO driverUpdateDTO, @MappingTarget Driver driverEntity);

    // mapeamentos auxiliares
    @BeanMapping(ignoreByDefault = true)
    Cnh toCnh(CnhRequestDTO dto);

    @BeanMapping(ignoreByDefault = true)
    Cnh toCnh(CnhUpdateDTO dto);

    @BeanMapping(ignoreByDefault = true)
    Address toAddress(AddressRequestDTO dto);

    @BeanMapping(ignoreByDefault = true)
    Address toAddressUpdate(AddressUpdateDTO dto);

}
