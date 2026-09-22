package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.dtos.request.CustomerRequestDTO;
import com.travel_system.backend_app.model.dtos.response.CustomerResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface CustomerResponseMapper {

    CustomerResponseDTO toDTO(Customer customer);

    default UUID map(Customer customer) {
        return customer != null ? customer.getId() : null;
    }
}
