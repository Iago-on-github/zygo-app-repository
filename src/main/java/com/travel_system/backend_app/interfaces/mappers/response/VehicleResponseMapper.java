package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.Vehicle;
import com.travel_system.backend_app.model.dtos.response.VehicleResponseDTO;
import com.travel_system.backend_app.utils.StorageUrlMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = StorageUrlMapper.class)
public interface VehicleResponseMapper {

    @Mapping(target = "vehicleImage", source = "vehicleImage", qualifiedByName = "toPublicUrl")
    VehicleResponseDTO toDTO(Vehicle vehicle);
}
