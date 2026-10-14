package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.Document;
import com.travel_system.backend_app.model.dtos.response.DocumentResponseDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface DocumentResponseMapper {

    DocumentResponseDTO toDTO(Document document);
}
