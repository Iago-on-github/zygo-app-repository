package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.CustomerMapper;
import com.travel_system.backend_app.interfaces.mappers.RouteStopResponseMapper;
import com.travel_system.backend_app.interfaces.mappers.StudentRouteStopResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.RouteStopAssignmentRequestDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopStudentUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopStudentsRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteRequestDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentRouteStopAssociateResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.repository.*;
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
import org.springframework.boot.convert.DataSizeUnit;
import org.testcontainers.shaded.org.bouncycastle.jcajce.provider.asymmetric.rsa.ISOSignatureSpi;

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
    private StandardRouteRepository standardRouteRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private StudentRouteStopAssignmentRepository studentRouteStopAssignmentRepository;

    StudentRouteStopAssignment studentRouteStopAssignment;
    StandardRoute standardRoute;
    UserModel user;
    Customer customer;
    RouteStop routeStop;
    Student studentOne;

    StandardRouteResponseDTO standardRouteResponseDTO;
    StandardRouteRequestDTO standardRouteRequestDTO;

    RouteStopStudentsRequestDTO routeStopStudentsRequestDTO;

    @BeforeEach
    void setUp() {
        StudentRouteStopResponseMapper realResponseMapper = Mappers.getMapper(StudentRouteStopResponseMapper.class);

        studentRouteStopService = new StudentRouteStopService(
                userRepository,
                routeStopRepository,
                studentRepository,
                standardRouteRepository,
                studentRouteStopAssignmentRepository,
                realResponseMapper
        );

        customer = new Customer();
        customer.setId(UUID.randomUUID());

        user = new UserModel(UUID.randomUUID(), "useremail@gmail.com", "123", "user", "lastname", "278382345", null, GeneralStatus.ACTIVE, LocalDateTime.now(), null, new Customer());

        Permissions perms = new Permissions("ROLE_ADMIN");
        user.setPermissions(List.of(perms));
        user.setCustomer(customer);

        standardRoute = new StandardRoute(UUID.randomUUID(), "Rota Universitária - Linha Leste", "Trajeto diário de transporte universitário conectando pontos de embarque ao campus central.", -12.2333, -38.7500, -12.2670, -38.9670, "a~|~Fkf~vO|@_@eA_@m@g@_@y@e@...", customer, GeneralStatus.ACTIVE, Instant.parse("2026-08-10T12:00:00Z"), Instant.parse("2026-08-10T12:00:00Z"));

        routeStop = new RouteStop(UUID.randomUUID(), "RouteStopName", "RouteStop Description", -45.324, -11.342, customer, GeneralStatus.ACTIVE, Instant.now(), null);

        studentOne = new Student();
        studentOne.setId(UUID.randomUUID());
        studentOne.setCustomer(routeStop.getCustomer());
        studentOne.setStatus(GeneralStatus.ACTIVE);

        studentRouteStopAssignment = new StudentRouteStopAssignment(UUID.randomUUID(), studentOne, routeStop, standardRoute, TravelPeriod.AFTERNOON, Instant.now(), null);
        
        standardRouteResponseDTO = new StandardRouteResponseDTO(standardRoute.getId(), "Rota Universitária - Linha Leste", "Trajeto diário de transporte universitário conectando pontos de embarque ao campus central.", -12.2333, -38.7500, -12.2670, -38.9670, "a~|~Fkf~vO|@_@eA_@m@g@_@y@e@...", Set.of(TravelPeriod.MORNING), Set.of(new RouteStopAssignmentResponseDTO(UUID.randomUUID(), "Ponto 1 - Praça Central", 1, false), new RouteStopAssignmentResponseDTO(UUID.randomUUID(), "Ponto 2 - Biblioteca", 2, true)), UUID.randomUUID(), GeneralStatus.ACTIVE, Instant.parse("2026-08-10T12:00:00Z"), Instant.parse("2026-08-10T12:00:00Z"));
        standardRouteRequestDTO = new StandardRouteRequestDTO(
                "Rota Coração de Maria - Feira de Santana",
                "Rota fictícia para testes saindo de Coração de Maria com destino a Feira de Santana.",
                -12.233333,
                -38.750000,
                -12.266666,
                -38.966666,
                Set.of(TravelPeriod.MORNING),
                Set.of(
                        new RouteStopAssignmentRequestDTO(
                                routeStop.getId(),
                                1,
                                false
                        )
                )
        );

        routeStopStudentsRequestDTO = new RouteStopStudentsRequestDTO(studentOne.getId(), TravelPeriod.MORNING);
    }

    @Nested
    class getStudentRouteStops {

        UUID standardRouteId;
        String userEmail;

        RouteStopStudentsRequestDTO routeStopStudentsRequestDTO;


        Student studentTwo;

        @BeforeEach
        void setUp() {
            standardRouteId = standardRoute.getId();
            userEmail = user.getEmail();

            studentTwo = new Student();
            studentTwo.setId(UUID.randomUUID());
            studentTwo.setCustomer(routeStop.getCustomer());
            studentTwo.setStatus(GeneralStatus.ACTIVE);
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve retornar os RouteStops do Student quando o ADMIN for válido")
            void shouldReturnStudentRouteStopsWhenUserIsAdminAndDataIsValid() {
                Permissions perms = new Permissions("ROLE_ADMIN");
                user.setPermissions(List.of(perms));

                when(userRepository.findUserByEmail(userEmail)).thenReturn(user);
                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(studentRouteStopAssignmentRepository.findByRouteStopIdAndStandardRouteId(routeStop.getId(), standardRoute.getId()))
                        .thenReturn(Set.of(studentRouteStopAssignment));
                when(studentRouteStopAssignmentRepository.findByStudentIdAndStandardRouteId(studentOne.getId(), standardRouteId))
                        .thenReturn(Optional.of(studentRouteStopAssignment));

                List<StudentRouteStopAssociateResponseDTO> result = studentRouteStopService.getStudentRouteStops(userEmail, studentOne.getId(), standardRoute.getId());

                assertFalse(result.isEmpty());

                assertEquals(1, result.size());

                StudentRouteStopAssociateResponseDTO dto = result.get(0);

                assertNotNull(dto);

                // validações de IDs principais
                assertEquals(standardRoute.getId(), dto.standardRoute().id());
                assertEquals(studentRouteStopAssignment.getRouteStop().getId(), dto.id());

                assertNotNull(dto.studentIds());
                assertTrue(dto.studentIds().contains(studentOne.getId()));

                verify(userRepository).findUserByEmail(userEmail);
                verify(standardRouteRepository).findById(standardRouteId);
                verify(studentRepository).findById(studentOne.getId());
                verify(studentRouteStopAssignmentRepository).findByStudentIdAndStandardRouteId(studentOne.getId(), standardRouteId);

            }

            @Test
            @DisplayName("Deve retornar os RouteStops do Student quando for realizada pelo mesmo (auto-consulta)")
            void shouldReturnStudentRouteStopsWhenUserIsStudentAndIgnorePassedStudentId() {
                Permissions perms = new Permissions("ROLE_USER");
                user.setPermissions(List.of(perms));

                when(userRepository.findUserByEmail(userEmail)).thenReturn(user);
                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(studentRouteStopAssignmentRepository.findByRouteStopIdAndStandardRouteId(routeStop.getId(), standardRoute.getId()))
                        .thenReturn(Set.of(studentRouteStopAssignment));
                when(studentRouteStopAssignmentRepository.findByStudentIdAndStandardRouteId(user.getId(), standardRouteId))
                        .thenReturn(Optional.of(studentRouteStopAssignment));

                List<StudentRouteStopAssociateResponseDTO> result = studentRouteStopService.getStudentRouteStops(userEmail, studentOne.getId(), standardRoute.getId());

                assertFalse(result.isEmpty());

                assertEquals(1, result.size());

                StudentRouteStopAssociateResponseDTO dto = result.get(0);

                assertNotNull(dto);

                // validações de IDs principais
                assertEquals(standardRoute.getId(), dto.standardRoute().id());
                assertEquals(studentRouteStopAssignment.getRouteStop().getId(), dto.id());

                assertNotNull(dto.studentIds());
                assertTrue(dto.studentIds().contains(studentOne.getId()));

                verify(userRepository).findUserByEmail(userEmail);
                verify(standardRouteRepository).findById(standardRouteId);

                verify(studentRepository, never()).findById(studentOne.getId());
            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o User não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.getStudentRouteStops(user.getEmail(), routeStop.getId(), standardRoute.getId()));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando não encontrar a Rota Padrão no banco")
            void shouldThrowEntityNotFoundExceptionWhenStandardRouteNotFound() {
                when(userRepository.findUserByEmail(userEmail)).thenReturn(user);
                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.getStudentRouteStops(userEmail, studentOne.getId(), standardRouteId));

                verifyNoMoreInteractions(standardRouteRepository, userRepository);

                verifyNoInteractions(studentRepository, studentRouteStopAssignmentRepository);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o user não for um STUDENT, ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRolesProvider")
            void shouldThrowNotAuthorizedExceptionWhenUserIsNotAdminOrPlatformAdmin(String invalidRole) {
                Permissions perm = new Permissions(invalidRole);
                user.setPermissions(List.of(perm));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));

                assertThrows(NotAuthorizedException.class, () -> studentRouteStopService.getStudentRouteStops(user.getEmail(), studentOne.getId(), standardRouteId));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository, studentRouteStopAssignmentRepository);
            }

            public static Stream<Arguments> invalidUserRolesProvider() {
                return Stream.of(
                        Arguments.of("ROLE_DRIVER")
                );
            }

            @Test
            @DisplayName("Deve lançar exception quando o Customer do User for diferente do Customer da Rota Padrão")
            void shouldThrowCustomerMismatchExceptionWhenRouteBelongsToDifferentCustomer() {
                standardRoute.setCustomer(new Customer());

                when(userRepository.findUserByEmail(userEmail)).thenReturn(user);
                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));

                assertThrows(CustomerMismatchException.class, () -> studentRouteStopService.getStudentRouteStops(user.getEmail(), studentOne.getId(), standardRouteId));

                verifyNoMoreInteractions(standardRouteRepository, userRepository);

                verifyNoInteractions(studentRepository, studentRouteStopAssignmentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o Customer do Usuário ADMIN for diferente do estudante buscado")
            void shouldThrowCustomerMismatchExceptionWhenTargetStudentBelongsToDifferentCustomer() {
                user.setCustomer(new Customer());

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));

                assertThrows(CustomerMismatchException.class, () -> studentRouteStopService.getStudentRouteStops(user.getEmail(), studentOne.getId(), standardRouteId));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository, studentRouteStopAssignmentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o estudante não for encontrado no fluxo de Admin")
            void shouldThrowEntityNotFoundExceptionWhenTargetStudentNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.getStudentRouteStops(user.getEmail(), studentOne.getId(), standardRouteId));

                verifyNoMoreInteractions(routeStopRepository, studentRepository, userRepository);

                verifyNoInteractions(studentRouteStopAssignmentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando a entidade de associação Student-RouteStop não for encontrada")
            void shouldThrowEntityNotFoundExceptionWhenAssignmentNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(studentRouteStopAssignmentRepository.findByStudentIdAndStandardRouteId(studentOne.getId(), standardRouteId))
                        .thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.getStudentRouteStops(user.getEmail(), studentOne.getId(), standardRouteId));

                verifyNoMoreInteractions(routeStopRepository, studentRepository, userRepository, studentRouteStopAssignmentRepository);
            }
        }
    }

    @Nested
    class getStudentRouteStopsByPeriodAndStandardRoute {

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realizar a consulta do Ponto de Parada por período e Rota Padrão com sucesso")
            void shouldReturnStudentRouteStopWhenAssignmentExistsAndDataIsValid() {
                // setup
                standardRoute.setTravelPeriods(Set.of(TravelPeriod.MORNING));
                studentRouteStopAssignment.setStandardRoute(standardRoute);
                studentRouteStopAssignment.setRouteStop(routeStop);
                studentRouteStopAssignment.setStudent(studentOne);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(studentRouteStopAssignmentRepository.findAssignmentByStudentRouteAndPeriod(
                        studentOne.getId(),
                        standardRoute.getId(),
                        TravelPeriod.MORNING,
                        customer.getId())).thenReturn(Optional.of(studentRouteStopAssignment));

                when(studentRouteStopAssignmentRepository.findByRouteStopIdAndStandardRouteId(routeStop.getId(), standardRoute.getId()))
                        .thenReturn(Set.of(studentRouteStopAssignment));

                StudentRouteStopAssociateResponseDTO result = studentRouteStopService.getStudentRouteStopsByPeriodAndStandardRoute(
                        user.getEmail(),
                        standardRoute.getId(),
                        routeStopStudentsRequestDTO
                );

                System.out.println("result: " + result);

                assertNotNull(result);

                assertEquals(standardRoute.getId(), result.standardRoute().id());
                assertEquals(routeStop.getId(), result.id());
                assertEquals(customer.getId(), result.customerId());

                assertNotNull(result.travelPeriods());
                assertTrue(result.travelPeriods().contains(TravelPeriod.MORNING));

                assertNotNull(result.studentIds());
                assertTrue(result.studentIds().contains(studentOne.getId()));

                verify(userRepository).findUserByEmail(user.getEmail());
                verify(standardRouteRepository).findById(standardRoute.getId());

                verify(studentRouteStopAssignmentRepository).findAssignmentByStudentRouteAndPeriod(studentOne.getId(), standardRoute.getId(), TravelPeriod.MORNING, customer.getId());

                verify(studentRouteStopAssignmentRepository).findByRouteStopIdAndStandardRouteId(routeStop.getId(), standardRoute.getId());

            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o User não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.getStudentRouteStops(user.getEmail(), routeStop.getId(), standardRoute.getId()));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando não encontrar a Rota Padrão no banco")
            void shouldThrowEntityNotFoundExceptionWhenStandardRouteNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.getStudentRouteStopsByPeriodAndStandardRoute(user.getEmail(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(standardRouteRepository, userRepository);

                verifyNoInteractions(studentRepository, studentRouteStopAssignmentRepository);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o user não for um STUDENT, ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRolesProvider")
            void shouldThrowNotAuthorizedExceptionWhenUserIsNotAdminOrPlatformAdmin(String invalidRole) {
                Permissions perm = new Permissions(invalidRole);
                user.setPermissions(List.of(perm));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(NotAuthorizedException.class, () -> studentRouteStopService.getStudentRouteStopsByPeriodAndStandardRoute(user.getEmail(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository, studentRouteStopAssignmentRepository);
            }

            public static Stream<Arguments> invalidUserRolesProvider() {
                return Stream.of(
                        Arguments.of("ROLE_DRIVER")
                );
            }

            @Test
            @DisplayName("Deve lançar exception quando o Customer do User for diferente do Customer da Rota Padrão")
            void shouldThrowCustomerMismatchExceptionWhenRouteBelongsToDifferentCustomer() {
                standardRoute.setCustomer(new Customer());

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(CustomerMismatchException.class, () -> studentRouteStopService.getStudentRouteStopsByPeriodAndStandardRoute(user.getEmail(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(standardRouteRepository, userRepository);

                verifyNoInteractions(studentRepository, studentRouteStopAssignmentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando a entidade de associação Student-RouteStop não for encontrada")
            void shouldThrowEntityNotFoundExceptionWhenAssignmentNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(studentRouteStopAssignmentRepository.findAssignmentByStudentRouteAndPeriod(any(), any(), any(), any()))
                        .thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.getStudentRouteStopsByPeriodAndStandardRoute(user.getEmail(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRepository, userRepository);
            }
        }
    }

    @Nested
    class associateStudentWithRouteStop {

        @BeforeEach
        void setUp() {
            RouteStopAssignment routeStopAssignment = new RouteStopAssignment();
            routeStopAssignment.setId(UUID.randomUUID());
            routeStopAssignment.setRouteStop(routeStop);
            routeStopAssignment.setStandardRoute(standardRoute);

            standardRoute.setRouteStopAssignments(List.of(routeStopAssignment));
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realizar a associação do Student com o RouteStop criando uma nova entidade de relacionamento para ambos com sucesso")
            void shouldAssociateStudentWithRouteStopAndReturnDtoWhenDataIsValid() {
                standardRoute.setTravelPeriods(Set.of(TravelPeriod.MORNING));
                studentRouteStopAssignment.setStandardRoute(standardRoute);
                studentRouteStopAssignment.setRouteStop(routeStop);
                studentRouteStopAssignment.setStudent(studentOne);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRouteStopAssignmentRepository.countByStudentId(studentOne.getId())).thenReturn(2L); // abaixo do limite de 3
                when(studentRouteStopAssignmentRepository.existsByStudentIdAndStandardRouteTravelPeriods(studentOne.getId(), TravelPeriod.MORNING)).thenReturn(false);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                when(studentRouteStopAssignmentRepository.save(any(StudentRouteStopAssignment.class))).thenAnswer(invocation -> {
                    StudentRouteStopAssignment saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    return saved;
                });

                when(studentRouteStopAssignmentRepository.findByRouteStopIdAndStandardRouteId(routeStop.getId(), standardRoute.getId()))
                        .thenReturn(Set.of(studentRouteStopAssignment));

                StudentRouteStopAssociateResponseDTO result = studentRouteStopService.associateStudentWithRouteStop(
                        user.getEmail(),
                        routeStop.getId(),
                        standardRoute.getId(),
                        routeStopStudentsRequestDTO
                );

                assertNotNull(result);

                assertEquals(customer.getId(), result.customerId());
                assertEquals(routeStop.getId(), result.id());
                assertEquals(standardRoute.getId(), result.standardRoute().id());

                assertEquals("RouteStopName", result.name());
                assertEquals("RouteStop Description", result.description());
                assertEquals(-45.324, result.latitude());
                assertEquals(-11.342, result.longitude());

                assertNotNull(result.travelPeriods());
                assertTrue(result.travelPeriods().contains(TravelPeriod.MORNING));

                assertNotNull(result.studentIds());
                assertTrue(result.studentIds().contains(studentOne.getId()));

                assertNotNull(result.createdAt());

                verify(userRepository).findUserByEmail(user.getEmail());
                verify(studentRouteStopAssignmentRepository).countByStudentId(studentOne.getId());
                verify(studentRouteStopAssignmentRepository).existsByStudentIdAndStandardRouteTravelPeriods(studentOne.getId(), TravelPeriod.MORNING);

                verify(studentRepository).findById(studentOne.getId());
                verify(routeStopRepository).findById(routeStop.getId());
                verify(standardRouteRepository).findById(standardRoute.getId());

                verify(studentRouteStopAssignmentRepository).save(any(StudentRouteStopAssignment.class));
                verify(studentRouteStopAssignmentRepository).findByRouteStopIdAndStandardRouteId(routeStop.getId(), standardRoute.getId());
            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o User não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o user não for um STUDENT, ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRolesProvider")
            void shouldThrowNotAuthorizedExceptionWhenUserIsNotAdminOrPlatformAdmin(String invalidRole) {
                Permissions perm = new Permissions(invalidRole);
                user.setPermissions(List.of(perm));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(NotAuthorizedException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository, studentRouteStopAssignmentRepository);
            }

            public static Stream<Arguments> invalidUserRolesProvider() {
                return Stream.of(
                        Arguments.of("ROLE_DRIVER")
                );
            }

            @Test
            @DisplayName("Deve lançar exception quando o StudentId provido do DTO for null")
            void shouldThrowIllegalArgumentExceptionWhenStudentIdIsNull() {
                RouteStopStudentsRequestDTO routeStopStudentsRequestDTOWithoutStudentId = new RouteStopStudentsRequestDTO(null, TravelPeriod.MORNING);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(IllegalArgumentException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTOWithoutStudentId));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository, studentRouteStopAssignmentRepository, standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o estudante atingir o limite de 3 pontos de parada")
            void shouldThrowDomainValidationExceptionWhenStudentReachesMaxAssignmentsLimit() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRouteStopAssignmentRepository.countByStudentId(studentOne.getId())).thenReturn(3L);

                assertThrows(DomainValidationException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository);

                verifyNoInteractions(studentRepository, standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o estudante já estiver com um Ponto de Parada para aquele turno")
            void shouldThrowIllegalArgumentExceptionWhenStudentAlreadyHasAssignmentInPeriod() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRouteStopAssignmentRepository.countByStudentId(studentOne.getId())).thenReturn(2L);
                when(studentRouteStopAssignmentRepository.existsByStudentIdAndStandardRouteTravelPeriods(studentOne.getId(), TravelPeriod.MORNING))
                        .thenReturn(true);

                assertThrows(IllegalArgumentException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository);

                verifyNoInteractions(studentRepository, standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o estudante não for encontrado")
            void shouldThrowExceptionWhenStudentNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRouteStopAssignmentRepository.countByStudentId(studentOne.getId())).thenReturn(2L);
                when(studentRouteStopAssignmentRepository.existsByStudentIdAndStandardRouteTravelPeriods(studentOne.getId(), TravelPeriod.MORNING))
                        .thenReturn(false);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository);

                verifyNoInteractions(standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o estudante não for encontrado")
            void shouldThrowExceptionWhenStudentIsInactive() {
                studentOne.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRouteStopAssignmentRepository.countByStudentId(studentOne.getId())).thenReturn(2L);
                when(studentRouteStopAssignmentRepository.existsByStudentIdAndStandardRouteTravelPeriods(studentOne.getId(), TravelPeriod.MORNING))
                        .thenReturn(false);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));

                assertThrows(InactiveAccountException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository);

                verifyNoInteractions(standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o Ponto de Parada não for encontrado")
            void shouldThrowExceptionWhenRouteStopNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRouteStopAssignmentRepository.countByStudentId(studentOne.getId())).thenReturn(2L);
                when(studentRouteStopAssignmentRepository.existsByStudentIdAndStandardRouteTravelPeriods(studentOne.getId(), TravelPeriod.MORNING))
                        .thenReturn(false);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository);

                verifyNoInteractions(standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o Ponto de Parada estiver Inativa")
            void shouldThrowExceptionWhenRouteStopIsInactive() {
                routeStop.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRouteStopAssignmentRepository.countByStudentId(studentOne.getId())).thenReturn(2L);
                when(studentRouteStopAssignmentRepository.existsByStudentIdAndStandardRouteTravelPeriods(studentOne.getId(), TravelPeriod.MORNING))
                        .thenReturn(false);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(IllegalArgumentException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository);

                verifyNoInteractions(standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando a Rota Padrão não for encontrada")
            void shouldThrowExceptionWhenStandardRouteNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRouteStopAssignmentRepository.countByStudentId(studentOne.getId())).thenReturn(2L);
                when(studentRouteStopAssignmentRepository.existsByStudentIdAndStandardRouteTravelPeriods(studentOne.getId(), TravelPeriod.MORNING))
                        .thenReturn(false);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando a Rota Padrão estiver Inativa")
            void shouldThrowExceptionWhenStandardRouteIsInactive() {
                standardRoute.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRouteStopAssignmentRepository.countByStudentId(studentOne.getId())).thenReturn(2L);
                when(studentRouteStopAssignmentRepository.existsByStudentIdAndStandardRouteTravelPeriods(studentOne.getId(), TravelPeriod.MORNING))
                        .thenReturn(false);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(IllegalArgumentException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }

            // Erros de incompatibilidade, turnos, customer, associações
            @Test
            @DisplayName("Deve lançar exception quando o período informado no DTO for difernete do período da Rota Padrão")
            void shouldThrowDomainValidationExceptionWhenTravelPeriodMismatchesStandardRoute() {
                standardRoute.setTravelPeriods(Set.of(TravelPeriod.EVENING));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRouteStopAssignmentRepository.countByStudentId(studentOne.getId())).thenReturn(2L);
                when(studentRouteStopAssignmentRepository.existsByStudentIdAndStandardRouteTravelPeriods(studentOne.getId(), TravelPeriod.MORNING))
                        .thenReturn(false);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(DomainValidationException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando houver disparidade de compatibilidade entre as entidades e o customer do user autenticado")
            @MethodSource("customerMismatchProvider")
            void shouldThrowCustomerMismatchExceptionWhenAnyEntityBelongsToDifferentCustomer(Customer randomCustomer) {
                standardRoute.setTravelPeriods(Set.of(TravelPeriod.MORNING));
                routeStop.setCustomer(randomCustomer);
                standardRoute.setCustomer(randomCustomer);
                studentOne.setCustomer(randomCustomer);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRouteStopAssignmentRepository.countByStudentId(studentOne.getId())).thenReturn(2L);
                when(studentRouteStopAssignmentRepository.existsByStudentIdAndStandardRouteTravelPeriods(studentOne.getId(), TravelPeriod.MORNING))
                        .thenReturn(false);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(CustomerMismatchException.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }

            public static Stream<Arguments> customerMismatchProvider() {
                return Stream.of(
                        Arguments.of(new Customer())
                );
            }

            @Test
            @DisplayName("Deve lançar exception quando o ponto de parada não fizer parte da rota padrão")
            void shouldThrowEntityAssignmentNotFoundWhenRouteStopIsNotInStandardRoute() {
                standardRoute.setTravelPeriods(Set.of(TravelPeriod.MORNING));
                standardRoute.setRouteStopAssignments(List.of());

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRouteStopAssignmentRepository.countByStudentId(studentOne.getId())).thenReturn(2L);
                when(studentRouteStopAssignmentRepository.existsByStudentIdAndStandardRouteTravelPeriods(studentOne.getId(), TravelPeriod.MORNING))
                        .thenReturn(false);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(EntityAssignmentNotFound.class, () -> studentRouteStopService.associateStudentWithRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }
        }
    }

    @Nested
    class updateStudentRouteStops {
        RouteStopStudentUpdateDTO routeStopStudentUpdateDTO;

        @BeforeEach
        void setUp() {
            RouteStopAssignment routeStopAssignment = new RouteStopAssignment();
            routeStopAssignment.setId(UUID.randomUUID());
            routeStopAssignment.setRouteStop(routeStop);
            routeStopAssignment.setStandardRoute(standardRoute);

            standardRoute.setRouteStopAssignments(List.of(routeStopAssignment));

            routeStopStudentUpdateDTO = new RouteStopStudentUpdateDTO(routeStop.getId(), TravelPeriod.AFTERNOON);
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realizar o Update do StudentRouteStop com sucesso")
            void shouldUpdateStudentRouteStopAndReturnDtoWhenDataIsValid() {
                standardRoute.setTravelPeriods(Set.of(TravelPeriod.AFTERNOON));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(studentRouteStopAssignmentRepository.findByStudentIdAndStandardRouteId(studentOne.getId(), standardRoute.getId()))
                        .thenReturn(Optional.of(studentRouteStopAssignment));
                when(studentRouteStopAssignmentRepository.save(any(StudentRouteStopAssignment.class))).thenAnswer(invocation -> {
                    StudentRouteStopAssignment savedStudentRouteStopAssignment = invocation.getArgument(0);
                    savedStudentRouteStopAssignment.setId(UUID.randomUUID());
                    savedStudentRouteStopAssignment.setCreatedAt(Instant.now());

                    return savedStudentRouteStopAssignment;
                });
                when(studentRouteStopAssignmentRepository.findByRouteStopIdAndStandardRouteId(routeStop.getId(), standardRoute.getId()))
                        .thenReturn(Set.of(studentRouteStopAssignment));

                StudentRouteStopAssociateResponseDTO result = studentRouteStopService.updateStudentRouteStops(user.getEmail(), studentOne.getId(), standardRoute.getId(), routeStopStudentUpdateDTO);

                assertNotNull(result);

                ArgumentCaptor<StudentRouteStopAssignment> assignmentArgumentCaptor = ArgumentCaptor.forClass(StudentRouteStopAssignment.class);
                verify(studentRouteStopAssignmentRepository, times(1)).save(assignmentArgumentCaptor.capture());

                StudentRouteStopAssignment savedValue = assignmentArgumentCaptor.getValue();

                // 1. Validações na Entidade Salva (Banco de Dados)
                assertEquals(routeStop.getId(), savedValue.getRouteStop().getId()); // O RouteStop se mantém
                assertEquals(studentOne.getId(), savedValue.getStudent().getId());  // O Student se mantém
                assertEquals(standardRoute.getId(), savedValue.getStandardRoute().getId()); // A Rota Padrão

                assertNotNull(result.id());
                assertEquals(customer.getId(), result.customerId());
                assertEquals(routeStop.getId(), result.id());
                assertEquals(standardRoute.getId(), result.standardRoute().id());

                assertNotNull(result.travelPeriods());
                assertTrue(result.travelPeriods().contains(TravelPeriod.AFTERNOON));

                assertNotNull(result.studentIds());
                assertTrue(result.studentIds().contains(studentOne.getId()));

                verify(userRepository).findUserByEmail(user.getEmail());
                verify(studentRepository).findById(studentOne.getId());
                verify(standardRouteRepository).findById(standardRoute.getId());
                verify(routeStopRepository).findById(routeStop.getId());
                verify(studentRouteStopAssignmentRepository).findByStudentIdAndStandardRouteId(studentOne.getId(), standardRoute.getId());

                verify(studentRouteStopAssignmentRepository).findByRouteStopIdAndStandardRouteId(routeStop.getId(), standardRoute.getId());

            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o User não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.updateStudentRouteStops(user.getEmail(), studentOne.getId(), standardRoute.getId(), routeStopStudentUpdateDTO));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o user não for um STUDENT, ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRolesProvider")
            void shouldThrowNotAuthorizedExceptionWhenUserIsNotAdminOrPlatformAdmin(String invalidRole) {
                Permissions perm = new Permissions(invalidRole);
                user.setPermissions(List.of(perm));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(NotAuthorizedException.class, () -> studentRouteStopService.updateStudentRouteStops(user.getEmail(), studentOne.getId(), standardRoute.getId(), routeStopStudentUpdateDTO));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository, studentRouteStopAssignmentRepository);
            }

            public static Stream<Arguments> invalidUserRolesProvider() {
                return Stream.of(
                        Arguments.of("ROLE_DRIVER")
                );
            }

            // erros durante busca de entidades
            @Test
            @DisplayName("Deve lançar exception quando o estudante não for encontrado")
            void shouldThrowExceptionWhenStudentNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.updateStudentRouteStops(user.getEmail(), studentOne.getId(), standardRoute.getId(), routeStopStudentUpdateDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository);

                verifyNoInteractions(standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o Ponto de Parada não for encontrado")
            void shouldThrowExceptionWhenRouteStopNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.updateStudentRouteStops(user.getEmail(), studentOne.getId(), standardRoute.getId(), routeStopStudentUpdateDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o Ponto de Parada estiver Inativa")
            void shouldThrowExceptionWhenRouteStopIsInactive() {
                routeStop.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(IllegalArgumentException.class, () -> studentRouteStopService.updateStudentRouteStops(user.getEmail(), studentOne.getId(), standardRoute.getId(), routeStopStudentUpdateDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository);

            }

            @Test
            @DisplayName("Deve lançar exception quando a Rota Padrão não for encontrada")
            void shouldThrowExceptionWhenStandardRouteNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.updateStudentRouteStops(user.getEmail(), studentOne.getId(), standardRoute.getId(), routeStopStudentUpdateDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando a Rota Padrão estiver Inativa")
            void shouldThrowExceptionWhenStandardRouteIsInactive() {
                standardRoute.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(IllegalArgumentException.class, () -> studentRouteStopService.updateStudentRouteStops(user.getEmail(), studentOne.getId(), standardRoute.getId(), routeStopStudentUpdateDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);

            }

            // validação das regras de negócio, turnos e conflitos
            @Test
            @DisplayName("Deve lançar exception quando o Período informado não é igual do Período da Rota Padrão")
            void shouldThrowIllegalArgumentExceptionWhenTravelPeriodNotInStandardRoutePeriods() {
                standardRoute.setTravelPeriods(Set.of(TravelPeriod.MORNING));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(IllegalArgumentException.class, () -> studentRouteStopService.updateStudentRouteStops(user.getEmail(), studentOne.getId(), standardRoute.getId(), routeStopStudentUpdateDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o Ponto de Parada não pertence à Rota Padrão")
            void shouldThrowEntityAssignmentNotFoundWhenNewRouteStopDoesNotBelongToRoute() {
                standardRoute.setTravelPeriods(Set.of(routeStopStudentUpdateDTO.travelPeriod()));
                standardRoute.setRouteStopAssignments(List.of());

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(EntityAssignmentNotFound.class, () -> studentRouteStopService.updateStudentRouteStops(user.getEmail(), studentOne.getId(), standardRoute.getId(), routeStopStudentUpdateDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o estudante não possuir vínculo com a Rota Padrão")
            void shouldThrowEntityAssignmentNotFoundWhenStudentAssignmentNotFound() {
                standardRoute.setTravelPeriods(Set.of(routeStopStudentUpdateDTO.travelPeriod()));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(studentRouteStopAssignmentRepository.findByStudentIdAndStandardRouteId(studentOne.getId(), standardRoute.getId()))
                        .thenReturn(Optional.empty());

                assertThrows(EntityAssignmentNotFound.class, () -> studentRouteStopService.updateStudentRouteStops(user.getEmail(), studentOne.getId(), standardRoute.getId(), routeStopStudentUpdateDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o estudante já possui OUTRO vínculo nesta rota para o mesmo turno")
            void shouldThrowDomainValidationExceptionWhenStudentAlreadyHasAnotherAssignmentInSamePeriod() {
                standardRoute.setTravelPeriods(Set.of(routeStopStudentUpdateDTO.travelPeriod()));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(studentRouteStopAssignmentRepository.findByStudentIdAndStandardRouteId(studentOne.getId(), standardRoute.getId()))
                        .thenReturn(Optional.of(studentRouteStopAssignment));
                when(studentRouteStopAssignmentRepository.existsByStudentIdAndStandardRouteIdAndTravelPeriodAndIdNot(
                        studentOne.getId(),
                        standardRoute.getId(),
                        routeStopStudentUpdateDTO.travelPeriod(), studentRouteStopAssignment.getId())).thenReturn(true);

                assertThrows(DomainValidationException.class, () -> studentRouteStopService.updateStudentRouteStops(user.getEmail(), studentOne.getId(), standardRoute.getId(), routeStopStudentUpdateDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }

            // divergencia de customers

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando houver disparidade de compatibilidade entre as entidades e o customer do user autenticado")
            @MethodSource("customerMismatchProvider")
            void shouldThrowCustomerMismatchExceptionWhenAnyEntityBelongsToDifferentCustomer(Customer randomCustomer) {
                routeStop.setCustomer(randomCustomer);
                standardRoute.setCustomer(randomCustomer);
                studentOne.setCustomer(randomCustomer);

                standardRoute.setTravelPeriods(Set.of(routeStopStudentUpdateDTO.travelPeriod()));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));

                assertThrows(CustomerMismatchException.class, () -> studentRouteStopService.updateStudentRouteStops(user.getEmail(), studentOne.getId(), standardRoute.getId(), routeStopStudentUpdateDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }

            public static Stream<Arguments> customerMismatchProvider() {
                return Stream.of(
                        Arguments.of(new Customer())
                );
            }
        }
    }

    @Nested
    class removeStudentFromRouteStop {
        RouteStopStudentsRequestDTO routeStopStudentsRequestDTO;

        @BeforeEach
        void setUp() {
            RouteStopAssignment routeStopAssignment = new RouteStopAssignment();
            routeStopAssignment.setId(UUID.randomUUID());
            routeStopAssignment.setRouteStop(routeStop);
            routeStopAssignment.setStandardRoute(standardRoute);

            standardRoute.setRouteStopAssignments(List.of(routeStopAssignment));

            routeStopStudentsRequestDTO = new RouteStopStudentsRequestDTO(studentOne.getId(), TravelPeriod.MORNING);
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realizar a remoção do estudante do ponto de parada com sucesso")
            void shouldRemoveStudentFromRouteStopAndReturnDtoWhenDataIsValid() {
                standardRoute.setTravelPeriods(Set.of(TravelPeriod.MORNING));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(studentRouteStopAssignmentRepository.findByStudentIdAndStandardRouteIdAndRouteStopId(
                        studentOne.getId(),
                        standardRoute.getId(),
                        routeStop.getId())).thenReturn(Optional.of(studentRouteStopAssignment));
                when(studentRouteStopAssignmentRepository.findByRouteStopIdAndStandardRouteId(routeStop.getId(), standardRoute.getId()))
                        .thenReturn(Set.of());

                StudentRouteStopAssociateResponseDTO result = studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO);

                assertNotNull(result);

                assertEquals(routeStop.getId(), result.id());
                assertEquals(standardRoute.getId(), result.standardRoute().id());
                assertEquals(customer.getId(), result.customerId());
                assertEquals(TravelPeriod.MORNING, result.travelPeriods().iterator().next());

                assertNotNull(result.studentIds());
                assertTrue(result.studentIds().isEmpty());

                verify(userRepository).findUserByEmail(user.getEmail());
                verify(studentRepository).findById(studentOne.getId());
                verify(routeStopRepository).findById(routeStop.getId());
                verify(standardRouteRepository).findById(standardRoute.getId());

                verify(studentRouteStopAssignmentRepository).findByStudentIdAndStandardRouteIdAndRouteStopId(studentOne.getId(), standardRoute.getId(), routeStop.getId());

                verify(studentRouteStopAssignmentRepository).delete(studentRouteStopAssignment);

                verify(studentRouteStopAssignmentRepository).findByRouteStopIdAndStandardRouteId(routeStop.getId(), standardRoute.getId());
            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o User não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o user não for um STUDENT, ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRolesProvider")
            void shouldThrowNotAuthorizedExceptionWhenUserIsNotAdminOrPlatformAdmin(String invalidRole) {
                Permissions perm = new Permissions(invalidRole);
                user.setPermissions(List.of(perm));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(NotAuthorizedException.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository, studentRouteStopAssignmentRepository);
            }

            public static Stream<Arguments> invalidUserRolesProvider() {
                return Stream.of(
                        Arguments.of("ROLE_DRIVER")
                );
            }

            // erros durante busca de entidades
            @Test
            @DisplayName("Deve lançar exception quando o estudante não for encontrado")
            void shouldThrowExceptionWhenStudentNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository);

                verifyNoInteractions(standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o Ponto de Parada não for encontrado")
            void shouldThrowExceptionWhenRouteStopNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(studentRouteStopAssignmentRepository, studentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando a Rota Padrão não for encontrada")
            void shouldThrowExceptionWhenStandardRouteNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));

                assertThrows(EntityNotFoundException.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }

            // erros de Status e Incompatibilidades de Domínio
            @Test
            @DisplayName("Deve lançar exception quando o Ponto de Parada estiver Inativa")
            void shouldThrowExceptionWhenRouteStopIsInactive() {
                routeStop.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(IllegalArgumentException.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository);

            }

            @Test
            @DisplayName("Deve lançar exception quando a Rota Padrão estiver Inativa")
            void shouldThrowExceptionWhenStandardRouteIsInactive() {
                standardRoute.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(IllegalArgumentException.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);

            }

            @Test
            @DisplayName("Deve lançar exception quando o Ponto de Parada não pertence à Rota Padrão")
            void shouldThrowEntityAssignmentNotFoundWhenNewRouteStopDoesNotBelongToRoute() {
                standardRoute.setTravelPeriods(Set.of(routeStopStudentsRequestDTO.travelPeriod()));
                standardRoute.setRouteStopAssignments(List.of());

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(EntityAssignmentNotFound.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }

            // validação das regras de negócio, turnos e conflitos
            @Test
            @DisplayName("Deve lançar exception quando o Período informado não é igual do Período da Rota Padrão")
            void shouldThrowIllegalArgumentExceptionWhenTravelPeriodNotInStandardRoutePeriods() {
                standardRoute.setTravelPeriods(Set.of(TravelPeriod.EVENING));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(DomainValidationException.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(routeStopRepository, studentRouteStopAssignmentRepository, studentRepository, standardRouteRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o estudante não possuir vínculo com a Rota Padrão")
            void shouldThrowEntityAssignmentNotFoundWhenStudentAssignmentNotFound() {
                standardRoute.setTravelPeriods(Set.of(routeStopStudentsRequestDTO.travelPeriod()));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(studentRepository.findById(studentOne.getId())).thenReturn(Optional.of(studentOne));
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(EntityAssignmentNotFound.class, () -> studentRouteStopService.removeStudentFromRouteStop(user.getEmail(), routeStop.getId(), standardRoute.getId(), routeStopStudentsRequestDTO));

                verifyNoMoreInteractions(studentRepository, standardRouteRepository);
            }
        }

    }
}