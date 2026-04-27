package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFound;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.exceptions.PermissionNotFoundException;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.request.StudentRequestDTO;
import com.travel_system.backend_app.model.dtos.response.StudentResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InstitutionType;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    /*
     * PADRÕES DOS TESTES UNITÁRIOS
     * 1. TESTAR TODOS AS SAÍDAS (RESULTADOS) DOS MÉTODOS EM QUESTÃO
     * 2. OS MÉTODOS SUCCESS DEVEM CONTER "WithSuccess"
     * 3. OS MÉTODOS FAILURE DEVEM CONTER "ThrowException"
     * 4. MÉTODOS COM VÁRIAS POSSÍVEIS SAÍDAS DEVEM SER OBRIGATORIAMENTE ENGLOBADAS EM CLASSES PRÓPRIAS DE SUCCESS E FAILURE (MESMO DENTRO DA SUA CLASSE DE ORIGEM)
     * 5. NUNCA USAR RUNTIME EX. COMO EXCEÇÃO CORINGA, USE A PRÓPRIA EXCEÇÃO LANÇADA NO MÉTODO
     * 6. SEMPRE ADICIONAR UMA BREVE DESCRIÇÃO COM A ANNOTATION '@DisplayName("...")'.
     * 7. OS TESTES DEVEM OBRIGATORIAMENTE SEGUIR O PADRÃO AAA (ARRANGE, ACT & ASSERT) de forma com que todos os cenários sejam cobertos
     *
     */

    @InjectMocks
    private StudentService studentService;

    @Mock
    private StudentRepository repository;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @Mock
    private PermissionsRepository permissionsRepository;

    Student student;

    StudentRequestDTO studentRequestDTO;

    @BeforeEach
    void setUp() {
        student = new Student(
                UUID.randomUUID(),
                "student@gmail.com",
                "senhaSegura123",
                "Student",
                "Teste",
                "75999999999",
                "teste_img",
                GeneralStatus.ACTIVE,
                LocalDateTime.now(),
                LocalDateTime.now(),
                InstitutionType.UNIVERSITY,
                "Ciência da Computação"
        );

        studentRequestDTO = new StudentRequestDTO(
                "student.test01@email.com",
                "Test@1234",
                "Lucas",
                "Oliveira",
                "71988887777",
                "imagem_teste",
                InstitutionType.UNIVERSITY,
                "Engenharia de Software"
        );
    }

    @Nested
    class getAllStudents {

        @Test
        @DisplayName("should return all students with success")
        void shouldReturnAllStudentsWithSuccess() {
            Student student = new Student(
                    UUID.randomUUID(),
                    "student@gmail.com",
                    "senhaSegura123",
                    "Student",
                    "Teste",
                    "75999999999",
                    "teste_img",
                    GeneralStatus.ACTIVE,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    InstitutionType.UNIVERSITY,
                    "Ciência da Computação"
            );

            when(repository.findAll()).thenReturn(List.of(student, student));

            List<StudentResponseDTO> result = studentService.getAllStudents();

            assertNotNull(result);

            assertEquals(student.getEmail(), result.getFirst().email());
            assertEquals(student.getId(), result.getFirst().id());
            assertEquals(student.getId(), result.getFirst().id());

            assertNotNull(result.getFirst().createdAt());
        }

        @Test
        @DisplayName("should return an empty list when students not found from database")
        void shouldReturnEmptyListWhenStudentsNotFound() {
            when(repository.findAll()).thenReturn(List.of());

            List<StudentResponseDTO> result = studentService.getAllStudents();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class getAllActiveStudents {

        @Test
        @DisplayName("should return all active students with success")
        void shouldReturnAllActiveStudentsWithSuccess() {
            GeneralStatus status = GeneralStatus.ACTIVE;

            when(repository.findAllByStatus(eq(status))).thenReturn(List.of(student, student));

            List<StudentResponseDTO> result = studentService.getAllActiveStudents();

            assertFalse(result.isEmpty());

            verify(repository, times(1)).findAllByStatus(any());
        }

        @Test
        @DisplayName("should return empty list when active students not found from database")
        void shouldReturnEmptyListWhenActiveStudentsNotFound(){
            GeneralStatus status = GeneralStatus.ACTIVE;

            when(repository.findAllByStatus(eq(status))).thenReturn(List.of());

            List<StudentResponseDTO> result = studentService.getAllActiveStudents();

            assertTrue(result.isEmpty());

            verify(repository, times(1)).findAllByStatus(any());
        }
    }

    @Nested
    class getAllInactiveStudents {

        @Test
        @DisplayName("should return all inactive students with success")
        void shouldReturnAllInactiveStudentsWithSuccess() {
            GeneralStatus status = GeneralStatus.INACTIVE;
            student.setStatus(status);

            when(repository.findAllByStatus(eq(status))).thenReturn(List.of(student, student));

            List<StudentResponseDTO> result = studentService.getAllInactiveStudents();

            assertFalse(result.isEmpty());

            verify(repository, times(1)).findAllByStatus(any());
        }

        @Test
        @DisplayName("should return empty list when inactive students not found from database")
        void shouldReturnEmptyListWhenInactiveStudentsNotFound(){
            GeneralStatus status = GeneralStatus.INACTIVE;
            student.setStatus(status);

            when(repository.findAllByStatus(eq(status))).thenReturn(List.of());

            List<StudentResponseDTO> result = studentService.getAllInactiveStudents();

            assertTrue(result.isEmpty());

            verify(repository, times(1)).findAllByStatus(any());
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

            when(repository.findByEmail(eq("student.test01@email.com"))).thenReturn(Optional.empty());
            when(repository.findByTelephone(eq("71988887777"))).thenReturn(Optional.empty());

            when(permissionsRepository.findByDescription("ROLE_USER")).thenReturn(Optional.of(new Permissions("ROLE_USER")));
            when(repository.save(any(Student.class))).thenReturn(student);


            StudentResponseDTO result = studentService.createStudent(studentRequestDTO);

            assertNotNull(result);

            ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);

            verify(repository, times(1)).save(studentCaptor.capture());
            Student storedValue = studentCaptor.getValue();

            assertEquals(studentRequestDTO.email(), storedValue.getEmail());
            assertTrue(passwordEncoder.matches("Test@1234", storedValue.getPassword()));
            assertEquals(studentRequestDTO.telephone(), storedValue.getTelephone());
            assertNotNull(storedValue.getCreatedAt());

            verify(repository, times(1)).findByEmail(any());
            verify(repository, times(1)).findByTelephone(any());
            verify(permissionsRepository, times(1)).findByDescription(any());
        }

        @ParameterizedTest
        @DisplayName("throw exception 'EmptyMandatoryFieldsFound' when any require fields are null ")
        @MethodSource("nullFieldsProvider")
        void throwExceptionWhenRequireDataFieldsAreNull(StudentRequestDTO dto) {
            assertThrows(EmptyMandatoryFieldsFound.class, () -> studentService.createStudent(dto));

            verify(passwordEncoder, never()).encode(anyString());

            verify(repository, never()).findByEmail(anyString());
            verify(repository, never()).findByTelephone(anyString());
            verify(repository, never()).save(any());

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
                            "https://cdn.test.com/avatar.png",
                            InstitutionType.UNIVERSITY,
                            "Direito"
                    ),
                    new StudentRequestDTO(
                            "student@email.com",
                            null,
                            "Carlos",
                            "Souza",
                            "71999999999",
                            "https://cdn.test.com/avatar.png",
                            InstitutionType.UNIVERSITY,
                            "Direito"
                    ),
                    new StudentRequestDTO(
                            "student@email.com",
                            "Test@123",
                            null,
                            "Souza",
                            "71999999999",
                            "https://cdn.test.com/avatar.png",
                            InstitutionType.UNIVERSITY,
                            "Direito"
                    ),
                    new StudentRequestDTO(
                            "student@email.com",
                            "Test@123",
                            "Carlos",
                            "Souza",
                            null,
                            "https://cdn.test.com/avatar.png",
                            InstitutionType.UNIVERSITY,
                            "Direito"
                    ), new StudentRequestDTO(
                            "student@email.com",
                            "Test@123",
                            "Carlos",
                            "Souza",
                            "71999999999",
                            "https://cdn.test.com/avatar.png",
                            null,
                            "Direito"
                    ),
                    new StudentRequestDTO(
                            "student@email.com",
                            "Test@123",
                            "Carlos",
                            "Souza",
                            "71999999999",
                            "https://cdn.test.com/avatar.png",
                            InstitutionType.UNIVERSITY,
                            null
                    )
            );
        }

        @Test
        @DisplayName("throw exception when email already registered by another user")
        void throwExceptionWhenEmailAlreadyRegistered() {
            when(repository.findByEmail("student.test01@email.com")).thenReturn(Optional.of(student));
            when(repository.findByTelephone(eq("71988887777"))).thenReturn(Optional.empty());

            assertThrows(DuplicateResourceException.class, () -> studentService.createStudent(studentRequestDTO));

            verify(passwordEncoder, times(1)).encode(anyString());
            verify(repository, times(1)).findByEmail(anyString());
            verify(repository, times(1)).findByTelephone(anyString());

            verify(repository, never()).save(any());

            verify(permissionsRepository, never()).findByDescription(anyString());
        }

        @Test
        @DisplayName("throw exception when telephone already registered by another user")
        void throwExceptionWhenTelephoneAlreadyRegistered() {
            when(repository.findByEmail("student.test01@email.com")).thenReturn(Optional.empty());
            when(repository.findByTelephone(eq("71988887777"))).thenReturn(Optional.of(student));

            assertThrows(DuplicateResourceException.class, () -> studentService.createStudent(studentRequestDTO));

            verify(passwordEncoder, times(1)).encode(anyString());
            verify(repository, times(1)).findByEmail(anyString());
            verify(repository, times(1)).findByTelephone(anyString());

            verify(repository, never()).save(any());

            verify(permissionsRepository, never()).findByDescription(anyString());
        }

        @Test
        @DisplayName("throw exception when permission not found from database")
        void throwExceptionWhenPermissionNotFound (){
            student.setPassword(passwordEncoder.encode("123"));
            student.setCreatedAt(LocalDateTime.now());

            when(repository.findByEmail(eq("student.test01@email.com"))).thenReturn(Optional.empty());
            when(repository.findByTelephone(eq("71988887777"))).thenReturn(Optional.empty());

            when(permissionsRepository.findByDescription(anyString())).thenReturn(Optional.empty());

            assertThrows(PermissionNotFoundException.class, () -> studentService.createStudent(studentRequestDTO));

            verify(repository, times(1)).findByEmail(any());
            verify(repository, times(1)).findByTelephone(any());

            verify(permissionsRepository, times(1)).findByDescription(anyString());

            verify(repository, never()).save(any());
        }
    }

    @Nested
    class updateLoggedStudent {

        @Test
        @DisplayName("should update logged student with success")
        void shouldUpdateLoggedStudentWithSuccess() {
            String authEmail = "authEmail@gmail.com";

            when(repository.findByEmail(authEmail)).thenReturn(Optional.of(student));
            when(repository.findByEmailOrTelephoneAndIdNot(studentRequestDTO.email(), studentRequestDTO.telephone(), student.getId()))
                    .thenReturn(Optional.empty());
            when(repository.save(any(Student.class))).thenReturn(student);

            StudentResponseDTO result = studentService.updateLoggedStudent(authEmail, studentRequestDTO);

            ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);

            verify(repository, times(1)).save(studentCaptor.capture());
            Student storedValue = studentCaptor.getValue();

            assertNotNull(result);

            assertAll(
                    () -> assertEquals(studentRequestDTO.email(), storedValue.getEmail()),
                    () -> assertTrue(passwordEncoder.matches("Test@1234", storedValue.getPassword())),
                    () -> assertEquals(studentRequestDTO.name(), storedValue.getName()),
                    () -> assertEquals(studentRequestDTO.lastName(), storedValue.getLastName()),
                    () -> assertEquals(studentRequestDTO.telephone(), storedValue.getTelephone()),
                    () -> assertEquals(studentRequestDTO.profilePicture(), storedValue.getProfilePicture()),
                    () -> assertEquals(studentRequestDTO.institutionType(), storedValue.getInstitutionType()),
                    () -> assertEquals(studentRequestDTO.course(), storedValue.getCourse())
            );

            verify(repository, times(1)).findByEmail(any());
            verify(repository, times(1)).findByEmailOrTelephoneAndIdNot(any(), any(), any());
        }

        @Test
        @DisplayName("throw exception when student not found from database")
        void throwExceptionWhenStudentNotFound() {
            when(repository.findByEmail("unexistingEmail@gmail.com")).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> studentService.updateLoggedStudent("unexistingEmail@gmail.com", studentRequestDTO));

            verify(repository, times(1)).findByEmail(any());

            verify(repository, never()).findByEmailOrTelephoneAndIdNot(any(), any(), any());
            verify(repository, never()).save(any(Student.class));

            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("throw exception when student was inactive from database")
        void throwExceptionWhenStudentWasInactive() {
            student.setStatus(GeneralStatus.INACTIVE);

            when(repository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));

            assertThrows(InactiveAccountModificationException.class, () -> studentService.updateLoggedStudent(student.getEmail(), studentRequestDTO));

            verify(repository, times(1)).findByEmail(any());

            verify(repository, never()).findByEmailOrTelephoneAndIdNot(any(), any(), any());
            verify(repository, never()).save(any(Student.class));

            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("throw exception if email/telephone/id already exists from database")
        void throwExceptionIfEmailTelephoneOrIdAlreadyExists() {
            when(repository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
            when(repository.findByEmailOrTelephoneAndIdNot(studentRequestDTO.email(), studentRequestDTO.telephone(), student.getId()))
                    .thenReturn(Optional.of(student));

            assertThrows(DuplicateResourceException.class, () -> studentService.updateLoggedStudent(student.getEmail(), studentRequestDTO));

            verify(repository, times(1)).findByEmail(any());
            verify(repository, times(1)).findByEmailOrTelephoneAndIdNot(any(), any(), any());

            verify(repository, never()).save(any(Student.class));

            verify(passwordEncoder, never()).encode(anyString());
        }
    }

    @Nested
    class getLoggedInStudentProfile {

        @Test
        @DisplayName("should get logged in student profile with success")
        void shouldGetLoggedInStudentProfileWithSuccess() {
            when(repository.findByEmail("email@gmail.com")).thenReturn(Optional.of(student));

            StudentResponseDTO result = studentService.getLoggedInStudentProfile("email@gmail.com");

            assertNotNull(result);

            verify(repository, times(1)).findByEmail(any());
        }

        @Test
        @DisplayName("throw exception when student not found from database")
        void throwExceptionWhenStudentNotFound() {
            when(repository.findByEmail("unexistingEmail@gmail.com")).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> studentService.getLoggedInStudentProfile("unexistingEmail@gmail.com"));

            verify(repository, times(1)).findByEmail(any());
        }
    }

    @Nested
    class disableStudent {

        @Test
        @DisplayName("should disable student with success")
        void shouldDisableStudentWithSuccess() {
            when(repository.findById(student.getId())).thenReturn(Optional.of(student));
            when(repository.save(any(Student.class))).thenReturn(student);

            studentService.disableStudent(student.getId());

            ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);

            verify(repository, times(1)).save(studentCaptor.capture());
            Student storedValue = studentCaptor.getValue();

            assertEquals(GeneralStatus.INACTIVE, storedValue.getStatus());
        }

        @Test
        @DisplayName("throw exception when student not found from database")
        void throwExceptionWhenStudentNotFound(){
            when(repository.findById(student.getId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> studentService.disableStudent(student.getId()));

            verify(repository, times(1)).findById(any());
            verify(repository, never()).save(any(Student.class));
        }

        @Test
        @DisplayName("throw exception when student was inactive from database")
        void throwExceptionWhenStudentWasInactive() {
            student.setStatus(GeneralStatus.INACTIVE);

            when(repository.findById(student.getId())).thenReturn(Optional.of(student));

            assertThrows(InactiveAccountModificationException.class, () -> studentService.disableStudent(student.getId()));

            verify(repository, never()).save(any(Student.class));
        }
    }
}