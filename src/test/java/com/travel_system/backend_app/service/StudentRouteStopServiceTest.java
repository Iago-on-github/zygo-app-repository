package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DomainValidationException;
import com.travel_system.backend_app.exceptions.InactiveAccountException;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.exceptions.NotAuthorizedException;
import com.travel_system.backend_app.interfaces.mappers.RouteStopResponseMapper;
import com.travel_system.backend_app.interfaces.mappers.StudentRouteStopResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.RouteStopAssignmentRequestDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopStudentsRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteRequestDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentRouteStopAssociateResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.repository.RouteStopRepository;
import com.travel_system.backend_app.repository.StandardRouteRepository;
import com.travel_system.backend_app.repository.StudentRepository;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentRouteStopServiceTest {
    
    private StudentRouteStopService studentRouteStopService;

    @Mock
    private RouteStopRepository routeStopRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentRepository studentRepository;

    StandardRoute standardRoute;
    UserModel user;
    Customer customer;
    RouteStop routeStop;

    StandardRouteResponseDTO standardRouteResponseDTO;
    StandardRouteRequestDTO standardRouteRequestDTO;

    @BeforeEach
    void setUp() {
        StudentRouteStopResponseMapper realResponseMapper = Mappers.getMapper(StudentRouteStopResponseMapper.class);

        studentRouteStopService = new StudentRouteStopService(
                userRepository,
                routeStopRepository,
                studentRepository,
                realResponseMapper
        );

        customer = new Customer();
        customer.setId(UUID.randomUUID());

        user = new UserModel(UUID.randomUUID(), "useremail@gmail.com", "123", "user", "lastname", "278382345", null, GeneralStatus.ACTIVE, LocalDateTime.now(), null, new Customer());

        Permissions perms = new Permissions("ROLE_ADMIN");
        user.setPermissions(List.of(perms));
        user.setCustomer(customer);

        standardRoute = new StandardRoute(UUID.randomUUID(), "Rota Universitária - Linha Leste", "Trajeto diário de transporte universitário conectando pontos de embarque ao campus central.", -12.2333, -38.7500, -12.2670, -38.9670, "a~|~Fkf~vO|@_@eA_@m@g@_@y@e@...", TravelPeriod.MORNING, customer, GeneralStatus.ACTIVE, Instant.parse("2026-08-10T12:00:00Z"), Instant.parse("2026-08-10T12:00:00Z"));

        routeStop = new RouteStop(UUID.randomUUID(), "RouteStopName", "RouteStop Description", -45.324, -11.342, customer, GeneralStatus.ACTIVE, Instant.now(), null);

        standardRouteResponseDTO = new StandardRouteResponseDTO(standardRoute.getId(), "Rota Universitária - Linha Leste", "Trajeto diário de transporte universitário conectando pontos de embarque ao campus central.", -12.2333, -38.7500, -12.2670, -38.9670, "a~|~Fkf~vO|@_@eA_@m@g@_@y@e@...", TravelPeriod.MORNING, Set.of(new RouteStopAssignmentResponseDTO(UUID.randomUUID(), "Ponto 1 - Praça Central", 1, false), new RouteStopAssignmentResponseDTO(UUID.randomUUID(), "Ponto 2 - Biblioteca", 2, true)), UUID.randomUUID(), GeneralStatus.ACTIVE, Instant.parse("2026-08-10T12:00:00Z"), Instant.parse("2026-08-10T12:00:00Z"));
        standardRouteRequestDTO = new StandardRouteRequestDTO(
                "Rota Coração de Maria - Feira de Santana",
                "Rota fictícia para testes saindo de Coração de Maria com destino a Feira de Santana.",
                -12.233333,
                -38.750000,
                -12.266666,
                -38.966666,
                TravelPeriod.MORNING,
                Set.of(
                        new RouteStopAssignmentRequestDTO(
                                routeStop.getId(),
                                1,
                                false
                        )
                )
        );
    }

    @Nested
    class associateStudentWithRouteStop {
        RouteStopStudentsRequestDTO routeStopStudentsRequestDTO;

        Student studentOne;
        Student studentTwo;
        @BeforeEach
        void setUp() {
            studentOne = new Student();
            studentOne.setId(UUID.randomUUID());
            studentOne.setCustomer(routeStop.getCustomer());
            studentOne.setStatus(GeneralStatus.ACTIVE);

            studentTwo = new Student();
            studentTwo.setId(UUID.randomUUID());
            studentTwo.setCustomer(routeStop.getCustomer());
            studentTwo.setStatus(GeneralStatus.ACTIVE);

            routeStopStudentsRequestDTO = new RouteStopStudentsRequestDTO(Set.of(studentOne.getId(), studentTwo.getId()));
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realizar a adição de estudantes no RouteStop com sucesso")
            void shouldAddStudentsToRouteStopWithSuccess() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(studentRepository.findAllById(routeStopStudentsRequestDTO.studentIds())).thenReturn(List.of(studentOne, studentTwo));
                when(routeStopRepository.save(routeStop)).thenAnswer(invocation -> {
                    RouteStop routestop = invocation.getArgument(0);
                    routestop.setId(UUID.randomUUID());
                    return routestop;
                });

                StudentRouteStopAssociateResponseDTO result = studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), routeStopStudentsRequestDTO);

                ArgumentCaptor<RouteStop> routeStopArgCaptor = ArgumentCaptor.forClass(RouteStop.class);

                verify(routeStopRepository, times(1)).save(routeStopArgCaptor.capture());

                RouteStop savedValue = routeStopArgCaptor.getValue();

                assertNotNull(savedValue);

                assertEquals(2, savedValue.getStudents().size());
                assertEquals(2, result.studentIds().size());

                assertEquals(savedValue.getStudents().stream().map(Student::getId).toList(), result.studentIds().stream().toList());
            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o User não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o user não tiver Customer")
            void shouldThrowDomainValidationExceptionWhenUserHasNoCustomer() {
                user.setCustomer(null);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(DomainValidationException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                verifyNoInteractions(studentRepository);
                verifyNoMoreInteractions(routeStopRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o user não estiver ATIVO no sistema")
            void shouldThrowInactiveAccountModificationExceptionWhenUserIsInactive() {
                user.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(InactiveAccountModificationException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o user não for um ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRolesProvider")
            void shouldThrowNotAuthorizedExceptionWhenUserIsNotAdminOrPlatformAdmin(String invalidRole) {
                Permissions perm = new Permissions(invalidRole);
                user.setPermissions(List.of(perm));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(NotAuthorizedException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository);
            }

            public static Stream<Arguments> invalidUserRolesProvider() {
                return Stream.of(
                        Arguments.of("ROLE_USER"),
                        Arguments.of("ROLE_DRIVER")
                );
            }

            @Test
            @DisplayName("Deve lançar exception quando o Student não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenStudentNotFound() {
                // student que nao está no dto
                Student mockStudent1 = new Student();
                mockStudent1.setId(UUID.randomUUID());
                mockStudent1.setCustomer(user.getCustomer());
                mockStudent1.setStatus(GeneralStatus.ACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(studentRepository.findAllById(anyCollection())).thenReturn(List.of(mockStudent1));

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), routeStopStudentsRequestDTO));

                verify(studentRepository).findAllById(anyCollection());
                verify(routeStopRepository, never()).save(any());
            }

            @Test
            @DisplayName("Deve lançar exceção quando um ou mais estudantes estiverem inativos")
            void shouldThrowInactiveAccountExceptionWhenStudentIsInactive() {
                Set<UUID> requestedIds = routeStopStudentsRequestDTO.studentIds();

                List<Student> mockedStudents = requestedIds.stream().map(id -> {
                    Student s = new Student();
                    s.setId(id);
                    s.setCustomer(user.getCustomer());
                    s.setStatus(GeneralStatus.ACTIVE);
                    return s;
                }).toList();

                Student inactiveStudent = mockedStudents.get(0);
                inactiveStudent.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(studentRepository.findAllById(anyCollection())).thenReturn(mockedStudents);

                InactiveAccountException exception = assertThrows(InactiveAccountException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                assertTrue(exception.getMessage().contains("Estudante Inativo no sistema:"));

                verify(studentRepository).findAllById(anyCollection());
                verify(routeStopRepository, never()).save(any());
            }
        }
    }

    @Nested
    class removeStudentFromRouteStop {

        RouteStopStudentsRequestDTO routeStopStudentsRequestDTO;

        Student studentOne;
        Student studentTwo;
        @BeforeEach
        void setUp() {
            studentOne = new Student();
            studentOne.setId(UUID.randomUUID());
            studentOne.setCustomer(routeStop.getCustomer());
            studentOne.setStatus(GeneralStatus.ACTIVE);

            studentTwo = new Student();
            studentTwo.setId(UUID.randomUUID());
            studentTwo.setCustomer(routeStop.getCustomer());
            studentTwo.setStatus(GeneralStatus.ACTIVE);

            routeStopStudentsRequestDTO = new RouteStopStudentsRequestDTO(Set.of(studentOne.getId(), studentTwo.getId()));

            routeStop.getStudents().add(studentOne);
            routeStop.getStudents().add(studentTwo);
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve remover o estudante do RouteStop com sucesso")
            void shouldRemoveStudentToRouteStopWithSuccess() {
                routeStop.setStudents(new HashSet<>(Set.of(studentOne, studentTwo)));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(studentRepository.findAllById(anyCollection())).thenReturn(List.of(studentOne, studentTwo));
                when(routeStopRepository.save(any(RouteStop.class))).thenAnswer(invocation -> invocation.getArgument(0));

                StudentRouteStopAssociateResponseDTO result = studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId(), routeStopStudentsRequestDTO);

                ArgumentCaptor<RouteStop> routeStopArgCaptor = ArgumentCaptor.forClass(RouteStop.class);
                verify(routeStopRepository, times(1)).save(routeStopArgCaptor.capture());

                RouteStop savedValue = routeStopArgCaptor.getValue();

                assertNotNull(savedValue);
                assertTrue(savedValue.getStudents().isEmpty());
                assertNull(result.studentIds());
            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o User não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o user não tiver Customer")
            void shouldThrowDomainValidationExceptionWhenUserHasNoCustomer() {
                user.setCustomer(null);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(DomainValidationException.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                verifyNoInteractions(studentRepository);
                verifyNoMoreInteractions(routeStopRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o user não estiver ATIVO no sistema")
            void shouldThrowInactiveAccountModificationExceptionWhenUserIsInactive() {
                user.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(InactiveAccountModificationException.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o user não for um ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRolesProvider")
            void shouldThrowNotAuthorizedExceptionWhenUserIsNotAdminOrPlatformAdmin(String invalidRole) {
                Permissions perm = new Permissions(invalidRole);
                user.setPermissions(List.of(perm));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(NotAuthorizedException.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository);
            }

            public static Stream<Arguments> invalidUserRolesProvider() {
                return Stream.of(
                        Arguments.of("ROLE_USER"),
                        Arguments.of("ROLE_DRIVER")
                );
            }

            @Test
            @DisplayName("Deve lançar exception quando o Student não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenStudentNotFound() {
                // student que nao está no dto
                Student mockStudent1 = new Student();
                mockStudent1.setId(UUID.randomUUID());
                mockStudent1.setCustomer(user.getCustomer());
                mockStudent1.setStatus(GeneralStatus.ACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(studentRepository.findAllById(anyCollection())).thenReturn(List.of(mockStudent1));

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId(), routeStopStudentsRequestDTO));

                verify(studentRepository).findAllById(anyCollection());
                verify(routeStopRepository, never()).save(any());
            }
        }

    }

}