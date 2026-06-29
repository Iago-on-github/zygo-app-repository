package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.StudentMapper;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.dtos.request.StudentUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.PermissionsRepository;
import com.travel_system.backend_app.repository.StudentRepository;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.request.StudentRequestDTO;
import com.travel_system.backend_app.model.dtos.response.StudentResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.checkerframework.checker.units.qual.Current;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StudentService {
    private final StudentRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionsRepository permissionsRepository;
    private final CustomerRepository customerRepository;
    private final StudentMapper studentMapper;
    private final CurrentUserService currentUserService;

    public StudentService(StudentRepository repository, PasswordEncoder passwordEncoder, PermissionsRepository permissionsRepository, CustomerRepository customerRepository, StudentMapper studentMapper, CurrentUserService currentUserService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.permissionsRepository = permissionsRepository;
        this.customerRepository = customerRepository;
        this.studentMapper = studentMapper;
        this.currentUserService = currentUserService;
    }

    public List<StudentResponseDTO> getAllStudents() {
        List<Student> getAllStudents = repository.findAll();

        return getAllStudents.stream().map(this::studentConverted).toList();
    }

    public List<StudentResponseDTO> getStudentsByStatus(GeneralStatus status) {
        if (status == null) status = GeneralStatus.ACTIVE;

        List<Student> students = repository.findAllByStatus(status);

        return students.stream().map(this::studentConverted).toList();
    }

    @Transactional
    public StudentResponseDTO createStudent(StudentRequestDTO requestDTO) {
        verifyFieldsIsNull(requestDTO);

        Optional<Student> email = repository.findByEmail(requestDTO.email());
        Optional<Student> telephone = repository.findByTelephone(requestDTO.telephone());

        if (email.isPresent()) throw new DuplicateResourceException("O email " + requestDTO.email() + " já existe");
        if (telephone.isPresent()) throw new DuplicateResourceException("O telefone " + requestDTO.telephone() + " já existe");

        final String PERM = "ROLE_USER";
        Permissions userPerm = permissionsRepository.findByDescription(PERM)
                .orElseThrow(() -> new PermissionNotFoundException("Permissão " + PERM + " não encontrada."));

        Customer customer = customerRepository.findById(requestDTO.customerId())
                .orElseThrow(() -> new EntityNotFoundException("Customer: " + requestDTO.customerId() + " não encontrado"));

        Student newStudent = studentMapper(requestDTO);

        newStudent.setPermissions(List.of(userPerm));
        newStudent.setCreatedAt(LocalDateTime.now());
        newStudent.setStatus(GeneralStatus.ACTIVE);
        newStudent.setCustomer(customer);

        Student savedStudent = repository.save(newStudent);
        return studentConverted(savedStudent);
    }

    @Transactional
    public StudentResponseDTO updateCurrentStudent(String authenticatedUserEmail, StudentUpdateDTO studentUpdateDTO) {
        Student studentEntity = repository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado, " + authenticatedUserEmail));

        if (studentEntity.getStatus().equals(GeneralStatus.INACTIVE)) {
          throw new InactiveAccountModificationException("Não é possível modificar dados de uma conta inativa: " + authenticatedUserEmail);
        }

        // verifica se email já existe
        if (studentUpdateDTO.email() != null && !studentUpdateDTO.email().equals(studentEntity.getEmail())) {
            boolean isEmailExists = repository.findByEmail(studentUpdateDTO.email()).isPresent();

            if (isEmailExists) throw new DuplicateResourceException("Email já em uso por outro usuário.");

            studentEntity.setEmail(studentUpdateDTO.email());
        }

        // verifica se telefone já existe
        if (studentUpdateDTO.telephone() != null && !studentUpdateDTO.telephone().equals(studentEntity.getTelephone())) {
            boolean isTelephoneExists = repository.findByTelephone(studentUpdateDTO.telephone()).isPresent();

            if (isTelephoneExists) throw new DuplicateResourceException("Telefone já em uso por outro usuário.");

            studentEntity.setTelephone(studentUpdateDTO.telephone());
        }

        // atualiza parcialmente sempre ignorando a senha
        studentMapper.studentUpdateFromDTO(studentUpdateDTO, studentEntity);

        // senha atualiza manualmente por conta do encrypt
        if (studentUpdateDTO.password() != null && !studentUpdateDTO.password().isBlank()) {
            studentEntity.setPassword(passwordEncoder.encode(studentUpdateDTO.password()));
        }
        
        Student savedStudent = repository.save(studentEntity);
        return studentConverted(savedStudent);
    }

    public StudentResponseDTO getCurrentStudent(String email) {
        Student student = repository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrato: " + email));

        return studentConverted(student);
    }

    @Transactional
    public void updateStudentStatus(UUID studentId, GeneralStatus newStatus) {
        Student student = repository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado, " + studentId));

        if (student.getStatus().equals(newStatus)) {
            throw new DuplicateResourceException("Estudante " + studentId + " já com o status " + newStatus);
        }

        student.setStatus(newStatus);
        student.setUpdatedAt(LocalDateTime.now());

        repository.save(student);
    }

    // MÉTODOS AUXILIARES
    // MÉTODOS AUXILIARES
    // MÉTODOS AUXILIARES

    private Student studentMapper(StudentRequestDTO requestDTO) {
        Student newStudent = new Student();

        newStudent.setEmail(requestDTO.email());
        newStudent.setPassword(passwordEncoder.encode(requestDTO.password()));
        newStudent.setName(requestDTO.name());
        newStudent.setLastName(requestDTO.lastName());
        newStudent.setTelephone(requestDTO.telephone());
        newStudent.setInstitutionType(requestDTO.institutionType());
        newStudent.setCourse(requestDTO.course());

        return newStudent;
    }

    private StudentResponseDTO studentConverted(Student student) {
        return new StudentResponseDTO(
                student.getId(),
                student.getName(),
                student.getLastName(),
                student.getEmail(),
                student.getTelephone(),
                student.getStatus(),
                currentUserService.getPublicUrl(student.getProfilePicture()),
                student.getCreatedAt(),
                student.getInstitutionType(),
                student.getCourse(),
                student.getCustomer().getId()
        );
    }

    private void verifyFieldsIsNull(StudentRequestDTO dto) {
        if (dto.email() == null || dto.password() == null ||
                dto.name() == null || dto.telephone() == null || dto.institutionType() == null || dto.course() == null
        || dto.customerId() == null) {
            throw new EmptyMandatoryFieldsFound("Você deve preencher todos os campos requeridos");
        }
    }

}
