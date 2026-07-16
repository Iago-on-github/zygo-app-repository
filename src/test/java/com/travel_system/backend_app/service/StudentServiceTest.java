package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFound;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.exceptions.PermissionNotFoundException;
import com.travel_system.backend_app.interfaces.mappers.StudentMapper;
import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.request.StudentRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StudentUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.StudentResponseDTO;
import com.travel_system.backend_app.model.enums.CitySize;
import com.travel_system.backend_app.model.enums.ClientSector;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.PermissionsRepository;
import com.travel_system.backend_app.repository.StudentRepository;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @InjectMocks
    private StudentService studentService;

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private CurrentUserService currentUserService;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @Mock
    private PermissionsRepository permissionsRepository;
    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private StudentMapper studentMapper;

    private final Pageable expectedPageable = PageRequest.of(0, 10);

    Student student;
    Customer customer;
    StudentRequestDTO studentRequestDTO;
    StudentUpdateDTO studentUpdateDTO;

    @BeforeEach
    void setUp() {
        City city = new City(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "São Paulo",
                CitySize.METROPOLIS,
                true
        );

        customer = new Customer(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Universidade Exemplo",
                "universidade-exemplo",
                "12.345.678/0001-90",
                true,
                city,
                ClientSector.PRIVATE_CLIENT,
                "https://cdn.exemplo.com/customers/universidade-exemplo.png",
                Instant.parse("2026-07-16T12:00:00Z"),
                Instant.parse("2026-07-16T12:00:00Z")
        );

        student = new Student(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "ana.souza@exemplo.com",
                "Senha@123",
                "Ana",
                "Souza",
                "+55 11 99999-1234",
                "https://cdn.exemplo.com/students/ana-souza.png",
                GeneralStatus.ACTIVE,
                LocalDateTime.of(2026, 7, 16, 12, 0),
                LocalDateTime.of(2026, 7, 16, 12, 0),
                customer,
                InstitutionType.UNIVERSITY,
                "Engenharia de Software"
        );

        studentRequestDTO = new StudentRequestDTO(
                "student.test01@email.com",
                "Test@1234",
                "Lucas",
                "Oliveira",
                "71988887777",
                InstitutionType.UNIVERSITY,
                "Engenharia de Software",
                customer.getId()
        );

        studentUpdateDTO = new StudentUpdateDTO(
                "student.test01@email.com",
                "Test@1234",
                "Lucas",
                "Oliveira",
                "71988887777",
                InstitutionType.UNIVERSITY,
                "Engenharia de Software"
        );
    }

    @Nested
    class getAllStudents {

        @Test
        void shouldRetrieveAndMapPaginatedStudentsSuccessfully() {
            Page<Student> pageStudent = new PageImpl<>(List.of(student));

            when(studentRepository.findAll(expectedPageable)).thenReturn(pageStudent);

            Page<StudentResponseDTO> result = studentService.getAllStudents();

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());

            assertEquals(student.getEmail(), result.getContent().getFirst().email());

            verify(studentRepository, times(1)).findAll(expectedPageable);
        }
    }

    @Nested
    class getStudentsByStatus {
        Page<Student> pageStudent;

        @BeforeEach
        void setUp() {
            when(currentUserService.getPublicUrl(student.getProfilePicture())).thenReturn("http://s3.url/pic.jpg");

            pageStudent = new PageImpl<>(List.of(student));

        }

        @Test
        @DisplayName("Deve retornar os students com base no status")
        void shouldReturnPaginatedStudentsByStatus() {
            when(studentRepository.findAllByStatus(GeneralStatus.INACTIVE, expectedPageable)).thenReturn(pageStudent);

            Page<StudentResponseDTO> result = studentService.getStudentsByStatus(GeneralStatus.INACTIVE);

            assertNotNull(result);
            assertEquals(1 ,result.getTotalElements());

            assertEquals(result.getContent().getFirst().id(), student.getId());
            assertEquals(result.getContent().getFirst().status(), student.getStatus());
        }

        @Test
        @DisplayName("Deve retornar os estudantes com base no status com ativação de fallback")
        void shouldReturnPaginatedStudentsByStatusWithFallback() {
            when(studentRepository.findAllByStatus(GeneralStatus.ACTIVE, expectedPageable)).thenReturn(pageStudent);

            Page<StudentResponseDTO> result = studentService.getStudentsByStatus(null);

            assertNotNull(result);
            assertEquals(1 ,result.getTotalElements());

            assertEquals(result.getContent().getFirst().id(), student.getId());
            assertEquals(result.getContent().getFirst().status(), student.getStatus());
        }
    }

    @Nested
    class createStudent {

        @Test
        @DisplayName("should create student with success")
        void shouldCreateStudentWithSuccess() {
            student.setPassword(passwordEncoder.encode("123"));
            student.setPermissions(List.of(new Permissions("ROLE_USER")));
            student.setCreatedAt(LocalDateTime.now());

            when(studentRepository.findByEmail(eq("student.test01@email.com"))).thenReturn(Optional.empty());
            when(studentRepository.findByTelephone(eq("71988887777"))).thenReturn(Optional.empty());

            when(permissionsRepository.findByDescription("ROLE_USER")).thenReturn(Optional.of(new Permissions("ROLE_USER")));
            when(studentRepository.save(any(Student.class))).thenReturn(student);
            when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

            StudentResponseDTO result = studentService.createStudent(studentRequestDTO);

            assertNotNull(result);

            ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);

            verify(studentRepository, times(1)).save(studentCaptor.capture());
            Student storedValue = studentCaptor.getValue();

            assertEquals(studentRequestDTO.email(), storedValue.getEmail());
            assertTrue(passwordEncoder.matches("Test@1234", storedValue.getPassword()));
            assertEquals(studentRequestDTO.telephone(), storedValue.getTelephone());
            assertNotNull(storedValue.getCreatedAt());

            verify(studentRepository, times(1)).findByEmail(any());
            verify(studentRepository, times(1)).findByTelephone(any());
            verify(permissionsRepository, times(1)).findByDescription(any());
        }

        @ParameterizedTest
        @DisplayName("throw exception 'EmptyMandatoryFieldsFound' when any require fields are null ")
        @MethodSource("nullFieldsProvider")
        void throwExceptionWhenRequireDataFieldsAreNull(StudentRequestDTO dto) {
            assertThrows(EmptyMandatoryFieldsFound.class, () -> studentService.createStudent(dto));

            verify(passwordEncoder, never()).encode(anyString());

            verify(studentRepository, never()).findByEmail(anyString());
            verify(studentRepository, never()).findByTelephone(anyString());
            verify(studentRepository, never()).save(any());

            verify(permissionsRepository, never()).findByDescription(anyString());
        }

        public static Stream<StudentRequestDTO> nullFieldsProvider() {
            return Stream.of(
                    new StudentRequestDTO(
                            null,
                            "Test@123",
                            "Carlos",
                            "Souza",
                            "71999999999",
                            InstitutionType.UNIVERSITY,
                            "Direito",
                            UUID.randomUUID()
                    ),
                    new StudentRequestDTO(
                            "student@email.com",
                            null,
                            "Carlos",
                            "Souza",
                            "71999999999",
                            InstitutionType.UNIVERSITY,
                            "Direito",
                            UUID.randomUUID()
                    ),
                    new StudentRequestDTO(
                            "student@email.com",
                            "Test@123",
                            null,
                            "Souza",
                            "71999999999",
                            InstitutionType.UNIVERSITY,
                            "Direito",
                            UUID.randomUUID()
                    ),
                    new StudentRequestDTO(
                            "student@email.com",
                            "Test@123",
                            "Carlos",
                            "Souza",
                            null,
                            InstitutionType.UNIVERSITY,
                            "Direito",
                            UUID.randomUUID()
                    ),
                    new StudentRequestDTO(
                            "student@email.com",
                            "Test@123",
                            "Carlos",
                            "Souza",
                            "71999999999",
                            null,
                            "Direito",
                            UUID.randomUUID()
                    ),
                    new StudentRequestDTO(
                            "student@email.com",
                            "Test@123",
                            "Carlos",
                            "Souza",
                            "71999999999",
                            InstitutionType.UNIVERSITY,
                            null,
                            UUID.randomUUID()
                    )
            );
        }

        @Test
        @DisplayName("throw exception when email already registered by another user")
        void throwExceptionWhenEmailAlreadyRegistered() {
            when(studentRepository.findByEmail("student.test01@email.com")).thenReturn(Optional.of(student));
            when(studentRepository.findByTelephone(eq("71988887777"))).thenReturn(Optional.empty());

            assertThrows(DuplicateResourceException.class, () -> studentService.createStudent(studentRequestDTO));


            verify(studentRepository, times(1)).findByEmail(anyString());
            verify(studentRepository, times(1)).findByTelephone(anyString());

            verify(studentRepository, never()).save(any());
            verify(permissionsRepository, never()).findByDescription(anyString());
            verify(passwordEncoder, never()).encode(anyString());
            verify(customerRepository, never()).findById(any());
        }

        @Test
        @DisplayName("throw exception when telephone already registered by another user")
        void throwExceptionWhenTelephoneAlreadyRegistered() {
            when(studentRepository.findByEmail("student.test01@email.com")).thenReturn(Optional.empty());
            when(studentRepository.findByTelephone(eq("71988887777"))).thenReturn(Optional.of(student));

            assertThrows(DuplicateResourceException.class, () -> studentService.createStudent(studentRequestDTO));

            verify(studentRepository, times(1)).findByEmail(anyString());
            verify(studentRepository, times(1)).findByTelephone(anyString());

            verify(studentRepository, never()).save(any());
            verify(permissionsRepository, never()).findByDescription(anyString());
            verify(customerRepository, never()).findById(any());
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("throw exception when permission not found from database")
        void throwExceptionWhenPermissionNotFound (){
            student.setPassword(passwordEncoder.encode("123"));
            student.setCreatedAt(LocalDateTime.now());

            when(studentRepository.findByEmail(eq("student.test01@email.com"))).thenReturn(Optional.empty());
            when(studentRepository.findByTelephone(eq("71988887777"))).thenReturn(Optional.empty());

            when(permissionsRepository.findByDescription(anyString())).thenReturn(Optional.empty());

            assertThrows(PermissionNotFoundException.class, () -> studentService.createStudent(studentRequestDTO));

            verify(studentRepository, times(1)).findByEmail(any());
            verify(studentRepository, times(1)).findByTelephone(any());

            verify(permissionsRepository, times(1)).findByDescription(anyString());

            verify(studentRepository, never()).save(any());
        }
    }

    @Nested
    class updateCurrentStudent {

        @Test
        @DisplayName("should update logged student with success")
        void shouldUpdateLoggedStudentWithSuccess() {
            String authEmail = "authEmail@gmail.com";

            when(studentRepository.findByEmail(authEmail)).thenReturn(Optional.of(student));
            when(studentRepository.save(any(Student.class))).thenReturn(student);

            doAnswer(invocation -> {
                StudentUpdateDTO dto = invocation.getArgument(0);
                Student entity = invocation.getArgument(1);

                entity.setName(dto.name());
                entity.setLastName(dto.lastName());
                entity.setCourse(dto.course());
                entity.setInstitutionType(dto.institutionType());
                entity.setTelephone(dto.telephone());

                return null;
            }).when(studentMapper).studentUpdateFromDTO(studentUpdateDTO, student);

            StudentResponseDTO result = studentService.updateCurrentStudent(authEmail, studentUpdateDTO);

            ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);

            verify(studentRepository, times(1)).save(studentCaptor.capture());
            Student storedValue = studentCaptor.getValue();

            assertNotNull(result);

            assertAll(
                    () -> assertEquals(studentUpdateDTO.email(), storedValue.getEmail()),
                    () -> assertTrue(passwordEncoder.matches("Test@1234", storedValue.getPassword())),
                    () -> assertEquals(studentUpdateDTO.name(), storedValue.getName()),
                    () -> assertEquals(studentUpdateDTO.lastName(), storedValue.getLastName()),
                    () -> assertEquals(studentUpdateDTO.telephone(), storedValue.getTelephone()),
                    () -> assertEquals(studentUpdateDTO.institutionType(), storedValue.getInstitutionType()),
                    () -> assertEquals(studentUpdateDTO.course(), storedValue.getCourse())
            );

            verify(studentRepository, times(2)).findByEmail(any());
            verify(studentRepository, times(1)).findByTelephone(any());
        }

        @Test
        @DisplayName("throw exception when student not found from database")
        void throwExceptionWhenStudentNotFound() {
            when(studentRepository.findByEmail("unexistingEmail@gmail.com")).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> studentService.updateCurrentStudent("unexistingEmail@gmail.com", studentUpdateDTO));

            verify(studentRepository, times(1)).findByEmail(any());

            verify(studentRepository, never()).findByEmailOrTelephoneAndIdNot(any(), any(), any());
            verify(studentRepository, never()).save(any(Student.class));

            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("throw exception when student was inactive from database")
        void throwExceptionWhenStudentWasInactive() {
            student.setStatus(GeneralStatus.INACTIVE);

            when(studentRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));

            assertThrows(InactiveAccountModificationException.class, () -> studentService.updateCurrentStudent(student.getEmail(), studentUpdateDTO));

            verify(studentRepository, times(1)).findByEmail(any());

            verify(studentRepository, never()).findByEmailOrTelephoneAndIdNot(any(), any(), any());
            verify(studentRepository, never()).save(any(Student.class));

            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        void throwExceptionIfEmailAlreadyExists() {

            when(studentRepository.findByEmail(any())).thenReturn(Optional.of(student));

            assertThrows(DuplicateResourceException.class, () -> studentService.updateCurrentStudent(student.getEmail(), studentUpdateDTO));

            verify(studentRepository, times(2)).findByEmail(any());

            verify(studentRepository, never()).save(any(Student.class));

            verify(passwordEncoder, never()).encode(anyString());
        }
    }

    @Nested
    class getCurrentStudent {

        @Test
        @DisplayName("should get logged in student profile with success")
        void shouldGetLoggedInStudentProfileWithSuccess() {
            when(studentRepository.findByEmail("email@gmail.com")).thenReturn(Optional.of(student));

            StudentResponseDTO result = studentService.getCurrentStudent("email@gmail.com");

            assertNotNull(result);

            verify(studentRepository, times(1)).findByEmail(any());
        }

        @Test
        @DisplayName("throw exception when student not found from database")
        void throwExceptionWhenStudentNotFound() {
            when(studentRepository.findByEmail("unexistingEmail@gmail.com")).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> studentService.getCurrentStudent("unexistingEmail@gmail.com"));

            verify(studentRepository, times(1)).findByEmail(any());
        }
    }

    @Nested
    class updateStudentStatus {

        @Test
        void shouldUpdateStudentStatusWithSuccess() {
            when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
            when(studentRepository.save(student)).thenReturn(student);

            studentService.updateStudentStatus(student.getId(), GeneralStatus.INACTIVE);

            ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);

            verify(studentRepository, times(1)).save(studentCaptor.capture());
            Student savedStudent = studentCaptor.getValue();

            assertEquals(GeneralStatus.INACTIVE, savedStudent.getStatus());
            assertNotNull(savedStudent.getUpdatedAt());
        }

        @Test
        void throwExceptionWhenStudentNotFound() {
            when(studentRepository.findById(student.getId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> studentService.updateStudentStatus(student.getId(), GeneralStatus.INACTIVE));

            verifyNoMoreInteractions(studentRepository);
        }

        @Test
        void throwExceptionWhenStudentAlreadyHasStatus() {
            when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));

            assertThrows(DuplicateResourceException.class, () -> studentService.updateStudentStatus(student.getId(), GeneralStatus.ACTIVE));

            verifyNoMoreInteractions(studentRepository);
        }
    }
}