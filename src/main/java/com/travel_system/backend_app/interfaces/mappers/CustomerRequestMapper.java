package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Address;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.dtos.request.*;
import org.mapstruct.*;
import org.springframework.context.annotation.Bean;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CustomerRequestMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "address", source = "addressRequest")
    Customer toEntity(CustomerOperationDataRequestDTO customerOperationDataRequestDTO);

    @Mapping(target = "name", source = "name")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "legalName", source = "legalName")
    @Mapping(target = "contactEmail", source = "contactEmail")
    @Mapping(target = "contactTelephone", source = "contactTelephone")
    @Mapping(target = "address", source = "addressRequest")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, ignoreByDefault = true)
    void updateEntityFromDTO(CustomerUpdateDTO customerUpdateDTO, @MappingTarget Customer customer);

    @Mapping(target = "contactEmail", source = "contactEmail")
    @Mapping(target = "contactTelephone", source = "contactTelephone")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, ignoreByDefault = true)
    void updateMyContactFromDTO(CustomerContactUpdateDTO customerContactUpdateDTO, @MappingTarget Customer customer);

    @Mapping(target = "shifts", source = "shifts")
    @Mapping(target = "operatingDays", source = "operatingDays")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, ignoreByDefault = true)
    void updateSettingsFromDTO(CustomerSettingsUpdateDTO dto, @MappingTarget Customer customer);

    @BeanMapping(ignoreByDefault = true)
    Address toAddress(AddressRequestDTO dto);
}
