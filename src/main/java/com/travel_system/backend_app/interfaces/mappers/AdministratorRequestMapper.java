package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Address;
import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.dtos.invitation.admin.AdministratorProfileDTO;
import com.travel_system.backend_app.model.dtos.request.AddressRequestDTO;
import com.travel_system.backend_app.model.dtos.request.AddressUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AdministratorRequestMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "jobTitle", source = "administratorInvitation.jobTitle")
    @Mapping(target = "name", source = "administratorAccept.name")
    @Mapping(target = "lastName", source = "administratorAccept.lastName")
    @Mapping(target = "cpf", source = "administratorAccept.cpf")
    @Mapping(target = "address", source = "administratorAccept.addressRequest")
    @Mapping(target = "birthdate", source = "administratorAccept.birthdate")
    @Mapping(target = "telephone", source = "administratorAccept.telephone")
    Administrator toEntity(AdministratorProfileDTO profileDTO);

    @Mapping(target = "address", source = "addressUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, ignoreByDefault = true)
    void administratorUpdateFromDTO(AdministratorUpdateDTO admUpdateDTO, @MappingTarget Administrator admEntity);

    @Mapping(target = "id", ignore = true)
    Address toAddress(AddressRequestDTO dto);

    @Mapping(target = "id", ignore = true)
    Address toAddressUpdate(AddressUpdateDTO dto);
}
