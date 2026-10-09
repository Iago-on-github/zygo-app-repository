package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Address;
import com.travel_system.backend_app.model.ResponsibleAdult;
import com.travel_system.backend_app.model.dtos.invitation.responsible.ResponsibleAdultProfileDTO;
import com.travel_system.backend_app.model.dtos.request.AddressRequestDTO;
import com.travel_system.backend_app.model.dtos.request.AddressUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.ResponsibleAdultUpdateDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ResponsibleAdultRequestMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "responsibleAdultAccept.name")
    @Mapping(target = "address", source = "responsibleAdultAccept.addressRequest")
    @Mapping(target = "lastName", source = "responsibleAdultAccept.lastName")
    @Mapping(target = "cpf", source = "responsibleAdultAccept.cpf")
    @Mapping(target = "telephone", source = "responsibleAdultAccept.telephone")
    @Mapping(target = "birthdate", source = "responsibleAdultAccept.birthdate")
    @Mapping(target = "responsibleAdultType", source = "responsibleAdultInvitation.responsibleAdultType")
    ResponsibleAdult toEntity(ResponsibleAdultProfileDTO profileDTO);

    @Mapping(target = "userAccount.email", source = "email")
    @Mapping(target = "address", source = "addressUpdate")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "cpf", source = "cpf")
    @Mapping(target = "telephone", source = "telephone")
    @Mapping(target = "birthdate", source = "birthdate")
    @Mapping(target = "responsibleAdultType", source = "responsibleAdultType")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, ignoreByDefault = true)
    ResponsibleAdult toUpdate(ResponsibleAdultUpdateDTO dto, @MappingTarget ResponsibleAdult responsibleAdult);

    // mapeamento auxiliares
    @BeanMapping(ignoreByDefault = true)
    Address toAddress(AddressRequestDTO dto);

    @BeanMapping(ignoreByDefault = true)
    Address toAddressUpdate(AddressUpdateDTO dto);
}
