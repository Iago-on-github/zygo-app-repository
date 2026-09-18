package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.dtos.response.CityResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface CityResponseMapper {

    CityResponseDTO toDTO(City city);

    default UUID mapCustomerToUUID(Customer customer) {
        if (customer == null) return null;

        return customer.getId();
    }
}
