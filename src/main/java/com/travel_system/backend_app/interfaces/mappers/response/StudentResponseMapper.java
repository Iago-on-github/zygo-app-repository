package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.response.StudentEnrollmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentResponseDTO;
import com.travel_system.backend_app.utils.StorageUrlMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {StudentEnrollmentResponseMapper.class, StorageUrlMapper.class})
public interface StudentResponseMapper {

    @Mapping(target = "email", source = "userAccount.email")
    @Mapping(target = "responsibleAdultId", source = "responsibleAdult.id")
    @Mapping(target = "addressResponse", source = "address")
    @Mapping(target = "shifts", source = "studentShift")
    @Mapping(target = "studentEnrollmentResponse", source = "enrollments")
    @Mapping(target = "profilePicture", source = "profilePicture", qualifiedByName = "toPublicUrl")
    StudentResponseDTO toDTO(Student student);
}
