package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.ResponsibleAdult;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.response.ResponsibleAdultResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentResponsibleAdultDTO;
import com.travel_system.backend_app.utils.StorageUrlMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring", uses = StorageUrlMapper.class)
public interface ResponsibleAdultResponseMapper {

    @Mapping(target = "email", source = "userAccount.email")
    @Mapping(target = "studentResponsibleAdultDTO", source = "students")
    @Mapping(target = "addressResponse", source = "address")
    @Mapping(target = "profilePicture", source = "profilePicture", qualifiedByName = "toPublicUrl")
    ResponsibleAdultResponseDTO toDTO(ResponsibleAdult responsibleAdult);

    @Mapping(target = "studentId", source = "id")
    StudentResponsibleAdultDTO toStudentDTO(Student student);

}
