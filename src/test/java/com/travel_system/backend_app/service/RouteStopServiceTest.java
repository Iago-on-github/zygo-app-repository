package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.RouteStopRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.RouteStopResponseMapper;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.*;
import com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RouteStopServiceTest {

    private RouteStopService routeStopService;
    
    @Mock
    private StandardRouteRepository standardRouteRepository;
    @Mock
    private RouteStopRepository routeStopRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentRepository studentRepository;
    
    @Mock
    private MapboxAPIService mapboxAPIService;
    
    StandardRoute standardRoute;
    UserModel user;
    Customer customer;
    RouteStop routeStop;

    StandardRouteResponseDTO standardRouteResponseDTO;
    StandardRouteRequestDTO standardRouteRequestDTO;

    @BeforeEach
    void setUp() {
        RouteStopRequestMapper realRequestMapper = Mappers.getMapper(RouteStopRequestMapper.class);
        RouteStopResponseMapper realResponseMapper = Mappers.getMapper(RouteStopResponseMapper.class);

        routeStopService = new RouteStopService(
                userRepository,
                routeStopRepository,
                studentRepository,
                mapboxAPIService,
                realResponseMapper,
                realRequestMapper
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
    class getRouteStopsByCustomer {

        @Test
        @DisplayName("Deve retornar os RouteStops pelo Customer com sucesso")
        void shouldGetRouteStopsByCustomer() {
            when(routeStopRepository.findRouteStopsByCustomerId(customer.getId())).thenReturn(List.of(routeStop));

            List<RouteStopResponseDTO> result = routeStopService.getRouteStopsByCustomer(customer.getId());

            assertNotNull(result);

            for (RouteStopResponseDTO each : result) {
                assertEquals(each.customerId(), customer.getId());
            }
        }

        @Test
        @DisplayName("Deve retornar uma lista vazia quando não encontrar nenhum RouteStop pelo Customer ID")
        void shouldReturnAnEmptyListWhenRouteStopsByCustomerNotFound() {
            when(routeStopRepository.findRouteStopsByCustomerId(customer.getId())).thenReturn(List.of());

            List<RouteStopResponseDTO> result = routeStopService.getRouteStopsByCustomer(customer.getId());

            assertEquals(0, result.size());
            assertEquals(Collections.emptyList(), result);
        }
    }

    @Nested
    class getRouteStopById {

        @Test
        @DisplayName("Deve retornar o RouteStop pelo ID com sucesso")
        void shouldReturnRouteStopById() {
            when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

            RouteStopResponseDTO result = routeStopService.getRouteStopById(routeStop.getId());

            assertNotNull(result);

            assertEquals(result.id(), routeStop.getId());
            assertEquals(result.customerId(), routeStop.getCustomer().getId());
        }

        @Test
        @DisplayName("Deve lançar exception quando não encontrar o routestop pelo id")
        void shouldThrowExceptionWhenRouteStopNotFound() {
            when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.empty());

            EntityNotFoundException exResult = assertThrows(EntityNotFoundException.class, () -> routeStopService.getRouteStopById(routeStop.getId()));

            assertEquals("RouteStop não encontrado: " + routeStop.getId(), exResult.getMessage());
        }
    }

    @Nested
    class createRouteStop {
        String userEmail;
        UUID routeStopId;

        RouteStopRequestDTO routeStopRequestDTO;
        RouteStopRequestDTO routeStopRequestDTOWithoutStudents;

        @BeforeEach
        void setUp() {
            userEmail = user.getEmail();
            routeStopId = routeStop.getId();

            routeStopRequestDTO = new RouteStopRequestDTO("RouteName", "RouteDescription", Set.of(UUID.randomUUID(), UUID.randomUUID()), -11.323, -38.232);
            routeStopRequestDTOWithoutStudents = new RouteStopRequestDTO("RouteName", "RouteDescription", Set.of(), -11.323, -38.232);
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve criar um novo RouteStop sem inclusão de estudantes com sucesso")
            void shouldCreateNewRouteStopWithoutStudents() {
                when(userRepository.findUserByEmail(userEmail)).thenReturn(user);
                when(routeStopRepository.existsByNameAndCustomerId(anyString(), any())).thenReturn(false);

                when(routeStopRepository.save(any(RouteStop.class))).thenAnswer(invocation -> {
                    RouteStop entityToSave = invocation.getArgument(0);
                    entityToSave.setId(UUID.randomUUID());
                    return entityToSave;
                });

                RouteStopResponseDTO result = routeStopService.createRouteStop(userEmail, routeStopRequestDTOWithoutStudents);

                ArgumentCaptor<RouteStop> routeStopArgCaptor = ArgumentCaptor.forClass(RouteStop.class);

                verify(routeStopRepository, times(1)).save(routeStopArgCaptor.capture());

                RouteStop savedValue = routeStopArgCaptor.getValue();

                assertNotNull(result);
                assertNotNull(savedValue);

                assertEquals(result.id(), savedValue.getId());
                assertEquals(0, savedValue.getStudents().size());
                assertEquals(result.customerId(), savedValue.getCustomer().getId());
                assertEquals(GeneralStatus.ACTIVE, savedValue.getStatus());

                assertNotNull(savedValue.getCreatedAt());

            }

            @Test
            @DisplayName("Deve criar um novo RouteStop com a inclusão de estudantes")
            void shouldCreateNewRouteStopWithStudents() {
                when(userRepository.findUserByEmail(userEmail)).thenReturn(user);
                when(routeStopRepository.existsByNameAndCustomerId(anyString(), any())).thenReturn(false);

                Student mockStudent1 = new Student();
                mockStudent1.setId(UUID.randomUUID());
                mockStudent1.setCustomer(user.getCustomer());
                mockStudent1.setStatus(GeneralStatus.ACTIVE);

                Student mockStudent2 = new Student();
                mockStudent2.setId(UUID.randomUUID());
                mockStudent2.setCustomer(user.getCustomer());
                mockStudent2.setStatus(GeneralStatus.ACTIVE);

                when(studentRepository.findAllById(any())).thenReturn(List.of(mockStudent1, mockStudent2));

                when(routeStopRepository.save(any(RouteStop.class))).thenAnswer(invocation -> {
                    RouteStop entityToSave = invocation.getArgument(0);
                    entityToSave.setId(UUID.randomUUID());
                    return entityToSave;
                });

                RouteStopResponseDTO result = routeStopService.createRouteStop(userEmail, routeStopRequestDTO);

                ArgumentCaptor<RouteStop> routeStopArgCaptor = ArgumentCaptor.forClass(RouteStop.class);

                verify(routeStopRepository, times(1)).save(routeStopArgCaptor.capture());

                RouteStop savedValue = routeStopArgCaptor.getValue();

                assertNotNull(result);
                assertNotNull(savedValue);

                System.out.println("resultId: " + result.studentIds());
                System.out.println("DTO: " + routeStopRequestDTO.studentIds());

                assertEquals(result.id(), savedValue.getId());
                assertEquals(2, result.studentIds().size());
                assertEquals(2, savedValue.getStudents().size()); // deve ter 2 students
                assertEquals(result.customerId(), savedValue.getCustomer().getId());
                assertEquals(GeneralStatus.ACTIVE, savedValue.getStatus());

                assertNotNull(savedValue.getCreatedAt());
            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o User não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(userEmail)).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> routeStopService.createRouteStop(userEmail, routeStopRequestDTO));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o user não tiver Customer")
            void shouldThrowDomainValidationExceptionWhenUserHasNoCustomer() {
                user.setCustomer(null);

                when(userRepository.findUserByEmail(userEmail)).thenReturn(user);

                assertThrows(DomainValidationException.class, () -> routeStopService.createRouteStop(userEmail, routeStopRequestDTO));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o user não estiver ATIVO no sistema")
            void shouldThrowInactiveAccountModificationExceptionWhenUserIsInactive() {
                user.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(userEmail)).thenReturn(user);

                assertThrows(InactiveAccountModificationException.class, () -> routeStopService.createRouteStop(userEmail, routeStopRequestDTO));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o user não for um ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRolesProvider")
            void shouldThrowNotAuthorizedExceptionWhenUserIsNotAdminOrPlatformAdmin(String invalidRole) {
                Permissions perm = new Permissions(invalidRole);
                user.setPermissions(List.of(perm));

                when(userRepository.findUserByEmail(userEmail)).thenReturn(user);

                assertThrows(NotAuthorizedException.class, () -> routeStopService.createRouteStop(userEmail, routeStopRequestDTO));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            public static Stream<Arguments> invalidUserRolesProvider() {
                return Stream.of(
                        Arguments.of("ROLE_USER"),
                        Arguments.of("ROLE_DRIVER")
                );
            }

            @Test
            @DisplayName("Deve lançar exception quando o 'name' do RouteStop já existir no banco para o Customer específco")
            void shouldThrowDuplicateResourceExceptionWhenRouteStopNameAlreadyExistsInCustomer() {
                when(userRepository.findUserByEmail(userEmail)).thenReturn(user);
                when(routeStopRepository.existsByNameAndCustomerId(anyString(), any())).thenReturn(true);

                assertThrows(DuplicateResourceException.class, () -> routeStopService.createRouteStop(userEmail, routeStopRequestDTO));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository);

            }

            @Test
            @DisplayName("Deve lançar exception quando o Student não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenStudentNotFound() {
                Student mockStudent1 = new Student();
                mockStudent1.setId(UUID.randomUUID());
                mockStudent1.setCustomer(user.getCustomer());
                mockStudent1.setStatus(GeneralStatus.ACTIVE);

                when(userRepository.findUserByEmail(userEmail)).thenReturn(user);
                when(routeStopRepository.existsByNameAndCustomerId(anyString(), any(UUID.class))).thenReturn(false);
                when(studentRepository.findAllById(anyCollection())).thenReturn(List.of(mockStudent1));

                assertThrows(EntityNotFoundException.class, () -> routeStopService.createRouteStop(userEmail, routeStopRequestDTO));

                verify(studentRepository).findAllById(anyCollection());
                verify(routeStopRepository, never()).save(any());
            }

            @Test
            @DisplayName("Deve lançar exceção quando um ou mais estudantes estiverem inativos")
            void shouldThrowInactiveAccountExceptionWhenStudentIsInactive() {
                Set<UUID> requestedIds = routeStopRequestDTO.studentIds();

                List<Student> mockedStudents = requestedIds.stream().map(id -> {
                    Student s = new Student();
                    s.setId(id);
                    s.setCustomer(user.getCustomer());
                    s.setStatus(GeneralStatus.ACTIVE);
                    return s;
                }).toList();

                Student inactiveStudent = mockedStudents.get(0);
                inactiveStudent.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(userEmail)).thenReturn(user);
                when(routeStopRepository.existsByNameAndCustomerId(anyString(), any(UUID.class))).thenReturn(false);
                when(studentRepository.findAllById(anyCollection())).thenReturn(mockedStudents);

                InactiveAccountException exception = assertThrows(InactiveAccountException.class, () -> routeStopService.createRouteStop(userEmail, routeStopRequestDTO));

                assertTrue(exception.getMessage().contains("Estudante Inativo no sistema:"));

                verify(studentRepository).findAllById(anyCollection());
                verify(routeStopRepository, never()).save(any());
            }

            @Test
            @DisplayName("Deve lançar exceção quando o estudante pertencer a um customer diferente")
            void shouldThrowExceptionWhenStudentBelongsToDifferentCustomer() {
                Set<UUID> requestedIds = routeStopRequestDTO.studentIds();

                Customer differentCustomer = new Customer();
                differentCustomer.setId(UUID.randomUUID());

                List<Student> mockedStudents = requestedIds.stream().map(id -> {
                    Student s = new Student();
                    s.setId(id);
                    s.setCustomer(differentCustomer);
                    s.setStatus(GeneralStatus.ACTIVE);
                    return s;
                }).toList();

                when(userRepository.findUserByEmail(userEmail)).thenReturn(user);
                when(routeStopRepository.existsByNameAndCustomerId(anyString(), any(UUID.class))).thenReturn(false);
                when(studentRepository.findAllById(anyCollection())).thenReturn(mockedStudents);

                assertThrows(RuntimeException.class, () -> routeStopService.createRouteStop(userEmail, routeStopRequestDTO));

                verify(studentRepository).findAllById(anyCollection());
                verify(routeStopRepository, never()).save(any());
            }
        }
    }
    
    @Nested 
    class updateRouteStop {
        RouteStopUpdateDTO routeStopUpdateDTO;

        @BeforeEach
        void setUp() {
            routeStopUpdateDTO = new RouteStopUpdateDTO("newRouteName", "newRouteDescritpion", -19.232, -49.123);
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realizar o update do RouteStop com sucesso")
            void shouldUpdateRouteStopWithSuccess() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(routeStopRepository.save(routeStop)).thenAnswer(invocation -> {
                    RouteStop routestop = invocation.getArgument(0);
                    routestop.setId(UUID.randomUUID());
                    return routestop;
                });

                RouteStopResponseDTO result = routeStopService.updateRouteStop(user.getEmail(), routeStop.getId(), routeStopUpdateDTO);

                ArgumentCaptor<RouteStop> routeStopArgCaptor = ArgumentCaptor.forClass(RouteStop.class);

                verify(routeStopRepository, times(1)).save(routeStopArgCaptor.capture());

                RouteStop savedValue = routeStopArgCaptor.getValue();

                assertEquals(savedValue.getName(), result.name());
                assertEquals(savedValue.getDescription(), result.description());
                assertEquals(savedValue.getLongitude(), result.longitude());
                assertEquals(savedValue.getLatitude(), result.latitude());
            }
        }

        @Nested
        class failureScenarios {
            @Test
            @DisplayName("Deve lançar exception quando o User não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> routeStopService.updateRouteStop(user.getEmail(), routeStop.getId() ,routeStopUpdateDTO));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o user não tiver Customer")
            void shouldThrowDomainValidationExceptionWhenUserHasNoCustomer() {
                user.setCustomer(null);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(DomainValidationException.class, () -> routeStopService.updateRouteStop(user.getEmail(), routeStop.getId() ,routeStopUpdateDTO));

                verifyNoInteractions(studentRepository);
                verifyNoMoreInteractions(routeStopRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o user não estiver ATIVO no sistema")
            void shouldThrowInactiveAccountModificationExceptionWhenUserIsInactive() {
                user.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(InactiveAccountModificationException.class, () -> routeStopService.updateRouteStop(user.getEmail(), routeStop.getId() ,routeStopUpdateDTO));

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

                assertThrows(NotAuthorizedException.class, () -> routeStopService.updateRouteStop(user.getEmail(), routeStop.getId() ,routeStopUpdateDTO));

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
            @DisplayName("Deve lançar exception quando o 'name' do RouteStop já existir no banco para o Customer específco")
            void shouldThrowDuplicateResourceExceptionWhenRouteStopNameAlreadyExistsInCustomer() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.existsByNameAndCustomerId(anyString(), any())).thenReturn(true);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(DuplicateResourceException.class, () -> routeStopService.updateRouteStop(user.getEmail(), routeStop.getId() ,routeStopUpdateDTO));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository);

            }

            @Test
            @DisplayName("Deve lançar exception quando as coordenadas não forem informadas juntas")
            void shouldThrowNoSuchCoordinatesWhenCoordinatesNotInformedTogether() {
                RouteStopUpdateDTO routeStopUpdateWithoutCoordinates = new RouteStopUpdateDTO("newRouteName", "newRouteDescritpion", null, -49.123);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.existsByNameAndCustomerId(anyString(), any())).thenReturn(false);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(NoSuchCoordinates.class, () -> routeStopService.updateRouteStop(user.getEmail(), routeStop.getId() ,routeStopUpdateWithoutCoordinates));

                verifyNoMoreInteractions(routeStopRepository);

                verifyNoInteractions(studentRepository);
            }
        }

    }

    @Nested
    class addStudentsToRouteStop {
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

                RouteStopResponseDTO result = routeStopService.addStudentsToRouteStop(user.getEmail(), routeStop.getId(), routeStopStudentsRequestDTO);

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

                assertThrows(EntityNotFoundException.class, () -> routeStopService.addStudentsToRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o user não tiver Customer")
            void shouldThrowDomainValidationExceptionWhenUserHasNoCustomer() {
                user.setCustomer(null);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(DomainValidationException.class, () -> routeStopService.addStudentsToRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                verifyNoInteractions(studentRepository);
                verifyNoMoreInteractions(routeStopRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o user não estiver ATIVO no sistema")
            void shouldThrowInactiveAccountModificationExceptionWhenUserIsInactive() {
                user.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(InactiveAccountModificationException.class, () -> routeStopService.addStudentsToRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

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

                assertThrows(NotAuthorizedException.class, () -> routeStopService.addStudentsToRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

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

                assertThrows(EntityNotFoundException.class, () -> routeStopService.addStudentsToRouteStop(user.getEmail(), routeStop.getId(), routeStopStudentsRequestDTO));

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

                InactiveAccountException exception = assertThrows(InactiveAccountException.class, () -> routeStopService.addStudentsToRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                assertTrue(exception.getMessage().contains("Estudante Inativo no sistema:"));

                verify(studentRepository).findAllById(anyCollection());
                verify(routeStopRepository, never()).save(any());
            }
        }
    }

    @Nested
    class removeStudentToRouteStop {

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

                RouteStopResponseDTO result = routeStopService.removeStudentToRouteStop(user.getEmail(), routeStop.getId(), routeStopStudentsRequestDTO);

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

                assertThrows(EntityNotFoundException.class, () -> routeStopService.removeStudentToRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                verifyNoInteractions(routeStopRepository, studentRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o user não tiver Customer")
            void shouldThrowDomainValidationExceptionWhenUserHasNoCustomer() {
                user.setCustomer(null);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(DomainValidationException.class, () -> routeStopService.removeStudentToRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

                verifyNoInteractions(studentRepository);
                verifyNoMoreInteractions(routeStopRepository);
            }

            @Test
            @DisplayName("Deve lançar exception quando o user não estiver ATIVO no sistema")
            void shouldThrowInactiveAccountModificationExceptionWhenUserIsInactive() {
                user.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(InactiveAccountModificationException.class, () -> routeStopService.removeStudentToRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

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

                assertThrows(NotAuthorizedException.class, () -> routeStopService.removeStudentToRouteStop(user.getEmail(), routeStop.getId() ,routeStopStudentsRequestDTO));

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

                assertThrows(EntityNotFoundException.class, () -> routeStopService.removeStudentToRouteStop(user.getEmail(), routeStop.getId(), routeStopStudentsRequestDTO));

                verify(studentRepository).findAllById(anyCollection());
                verify(routeStopRepository, never()).save(any());
            }
        }

    }

    @Nested
    class updateRouteStopStatus {

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realizar o update do status do Route Stop com sucesso")
            void shouldUpdateRouteStopStatusWhenDataIsValid() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(routeStopRepository.save(routeStop)).thenReturn(routeStop);

                routeStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE);

                ArgumentCaptor<RouteStop> routeStopArgCaptor = ArgumentCaptor.forClass(RouteStop.class);

                verify(routeStopRepository, times(1)).save(routeStopArgCaptor.capture());

                RouteStop storageValue = routeStopArgCaptor.getValue();

                assertEquals(GeneralStatus.INACTIVE, storageValue.getStatus());

            }
        }

        @Nested
        class failureScenarios {
            @Test
            @DisplayName("Deve lançar exception quando o usuário não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> routeStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verifyNoInteractions(standardRouteRepository, standardRouteRepository, mapboxAPIService);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception se o usuário não tiver a ROLE de ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRoleProvider")
            void shouldThrowExceptionWhenUserIsNotAdmin(String permission) {
                Permissions invalidPerms = new Permissions(permission);
                user.setPermissions(List.of(invalidPerms));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(NotAuthorizedException.class, () -> routeStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(standardRouteRepository, routeStopRepository, mapboxAPIService);
            }

            public static Stream<Arguments> invalidUserRoleProvider() {
                return Stream.of(
                        Arguments.of("ROLE_DRIVER"),
                        Arguments.of("ROLE_USER")
                );
            }

            @Test
            void shouldThrowExceptionWhenAdminIsWithoutCustomer() {
                user.setCustomer(null);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(DomainValidationException.class, () -> routeStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(standardRouteRepository, routeStopRepository, mapboxAPIService);
            }

            @Test
            void shouldThrowExceptionWhenAdminIsInactive() {
                user.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(InactiveAccountModificationException.class, () -> routeStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(standardRouteRepository, routeStopRepository, mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando o Customer da rota padrão for diferente do Customer do usuário autenticado")
            void shouldThrowExceptionWhenRouteBelongsToDifferentCustomer() {
                Customer differentCustomer = new Customer();
                user.setCustomer(differentCustomer);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(CustomerMismatchException.class, () -> routeStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verify(routeStopRepository, never()).save(any());

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(mapboxAPIService);

            }

            @Test
            @DisplayName("Deve lançar exception quando o RouteStop não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenRouteStopNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> routeStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(routeStopRepository, userRepository);

                verifyNoInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando o routeStop ja possuir o status do parâmetro")
            void shouldThrowDuplicateResourceExceptionWhenStatusIsSameAsCurrent() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(DuplicateResourceException.class, () -> routeStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.ACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(routeStopRepository, never()).save(any());

                verifyNoMoreInteractions(routeStopRepository, userRepository);

                verifyNoInteractions(mapboxAPIService);
            }
        }
    }
}