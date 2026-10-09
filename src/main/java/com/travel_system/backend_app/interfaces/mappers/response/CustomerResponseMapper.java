package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.dtos.response.CustomerOperationDataResponseDTO;
import com.travel_system.backend_app.model.dtos.response.CustomerPlanUsageResponseDTO;
import com.travel_system.backend_app.model.dtos.response.CustomerSettingsResponseDTO;
import com.travel_system.backend_app.utils.StorageUrlMapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring", uses = StorageUrlMapper.class)
public interface CustomerResponseMapper {

    @Mapping(target = "cityName", source = "city.name")
    @Mapping(target = "addressResponse", source = "address")
    @Mapping(target = "logoUrl", source = "logoUrl", qualifiedByName = "toPublicUrl")
    CustomerOperationDataResponseDTO toDTO(Customer customer);

    @Mapping(target = "customerOperationDataResponse", source = "customer")
    CustomerSettingsResponseDTO toSettingsDTO(Customer customer);

    default UUID map(Customer customer) {
        return customer != null ? customer.getId() : null;
    }
}
