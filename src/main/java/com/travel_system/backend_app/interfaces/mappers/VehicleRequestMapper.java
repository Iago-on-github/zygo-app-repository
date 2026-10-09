package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Vehicle;
import com.travel_system.backend_app.model.dtos.request.VehicleRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleUpdateDTO;
import com.travel_system.backend_app.utils.StorageUrlMapper;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VehicleRequestMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "vehicleNumber", source = "vehicleNumber")
    @Mapping(target = "vehicleType", source = "vehicleType")
    @Mapping(target = "numberPlate", source = "numberPlate")
    @Mapping(target = "color", source = "color")
    Vehicle toEntity(VehicleRequestDTO vehicleRequestDTO);

    @Mapping(target = "vehicleNumber", source = "vehicleNumber")
    @Mapping(target = "vehicleType", source = "vehicleType")
    @Mapping(target = "numberPlate", source = "numberPlate")
    @Mapping(target = "color", source = "color")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, ignoreByDefault = true)
    void updateFromDTO(VehicleUpdateDTO dto, @MappingTarget Vehicle vehicle);
}
