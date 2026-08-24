package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.*;
import com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.repository.RouteStopRepository;
import com.travel_system.backend_app.repository.StandardRouteRepository;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StandardRouteServiceTest {

    private StandardRouteService standardRouteService;

    @Mock
    private StandardRouteRepository standardRouteRepository;
    @Mock
    private RouteStopRepository routeStopRepository;
    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private MapboxAPIService mapboxAPIService;

    private final Pageable expectedPageable = PageRequest.of(0, 10);

    StandardRoute standardRoute;
    UserModel user;
    Customer customer;
    RouteStop routeStop;

    StandardRouteResponseDTO standardRouteResponseDTO;
    StandardRouteRequestDTO standardRouteRequestDTO;

    @BeforeEach
    void setUp() {
        StandardRouteResponseMapper realResponseMapper = Mappers.getMapper(StandardRouteResponseMapper.class);
        StandardRouteRequestMapper realRequestMapper = Mappers.getMapper(StandardRouteRequestMapper.class);

        standardRouteService = new StandardRouteService(
                standardRouteRepository,
                routeStopRepository,
                userRepository,
                realRequestMapper,
                realResponseMapper,
                currentUserService,
                mapboxAPIService
        );

        customer = new Customer();
        customer.setId(UUID.randomUUID());

        user = new UserModel(UUID.randomUUID(), "useremail@gmail.com", "123", "user", "lastname", "278382345", null, GeneralStatus.ACTIVE, LocalDateTime.now(), null, new Customer());

        Permissions perms = new Permissions("ROLE_ADMIN");
        user.setPermissions(List.of(perms));
        user.setCustomer(customer);

        standardRoute = new StandardRoute(UUID.randomUUID(), "Rota Universitária - Linha Leste", "Trajeto diário de transporte universitário conectando pontos de embarque ao campus central.", -12.2333, -38.7500, -12.2670, -38.9670, "a~|~Fkf~vO|@_@eA_@m@g@_@y@e@...", customer, GeneralStatus.ACTIVE, Instant.parse("2026-08-10T12:00:00Z"), Instant.parse("2026-08-10T12:00:00Z"));

        routeStop = new RouteStop(UUID.randomUUID(), "RouteStopName", "RouteStop Description", -45.324, -11.342, customer, GeneralStatus.ACTIVE, Instant.now(), null);

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
    }

    @Nested
    class getAllStandardRoutes {

        @Test
        @DisplayName("Deve retornar todas as rotas padrões cadastradas no banco com paginação")
        void shouldGetAllStandardRoutes() {
            Page<StandardRoute> pagedStandardRoute = new PageImpl<>(List.of(standardRoute));

            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(standardRouteRepository.findAll(expectedPageable)).thenReturn(pagedStandardRoute);

            Page<StandardRouteResponseDTO> result = standardRouteService.getAllStandardRoutes();

            assertNotNull(result);
            assertTrue(result.getSize() >= 1);

            verify(currentUserService, times(1)).isPlatformAdmin();
            verify(standardRouteRepository, times(1)).findAll(eq(expectedPageable));
        }

        @Test
        @DisplayName("Deve lançar exception quando o usuário não for um Platform Admin")
        void shouldThrowExceptionWhenIsNotPlatformAdmin() {
            when(currentUserService.isPlatformAdmin()).thenReturn(false);

            assertThrows(NotAuthorizedException.class, () -> standardRouteService.getAllStandardRoutes());

            verify(currentUserService, times(1)).isPlatformAdmin();
            verify(standardRouteRepository, never()).findAll(eq(expectedPageable));
        }
    }

    @Nested
    class getStandardRouteById {

        @Test
        @DisplayName("Deve retornar a rota padrão pelo seu ID com sucesso")
        void shouldReturnStandardRouteById() {
            when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

            StandardRouteResponseDTO result = standardRouteService.getStandardRouteById(standardRoute.getId());

            assertNotNull(result);

            assertEquals(result.id(), standardRouteResponseDTO.id());
            assertEquals(result.routeName(), standardRouteResponseDTO.routeName());
            assertEquals(result.standardGeometry(), standardRouteResponseDTO.standardGeometry());

            verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));
        }

        @Test
        @DisplayName("Deve lançar exception quando StandardRoute não existir")
        void shouldThrowExceptionWhenStandardRouteDoesNotExists() {
            when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> standardRouteService.getStandardRouteById(standardRoute.getId()));

            verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));
        }
    }

    @Nested
    class getAllStandardRouteByCustomer {

        @Test
        @DisplayName("")
        void shouldReturnAllStandardRouteByCustomer() {
            UUID customerId = UUID.randomUUID();

            Page<StandardRoute> pagedStandardRoute = new PageImpl<>(List.of(standardRoute));

            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(standardRouteRepository.findAllByCustomerId(customerId, expectedPageable)).thenReturn(pagedStandardRoute);

            Page<StandardRouteResponseDTO> result = standardRouteService.getAllStandardRouteByCustomer(customerId);

            assertFalse(result.isEmpty());

            assertTrue(result.getSize() >= 1);
        }

        @Test
        @DisplayName("Deve lançar exception quando o usuário não for um Platform Admin")
        void shouldThrowExceptionWhenIsNotPlatformAdmin() {
            when(currentUserService.isPlatformAdmin()).thenReturn(false);

            assertThrows(NotAuthorizedException.class, () -> standardRouteService.getAllStandardRouteByCustomer(UUID.randomUUID()));

            verify(currentUserService, times(1)).isPlatformAdmin();
            verify(standardRouteRepository, never()).findAllByCustomerId(any(), any());
        }
    }

    @Nested
    class getStandardRouteStopPoints {

        @Test
        @DisplayName("Deve recuperar os pontos de parada de uma rota padrão com sucesso")
        void shouldGetStandardRouteStopPoints() {
            when(standardRouteRepository.findRouteBaseByIdAndStatus(standardRoute.getId(), GeneralStatus.ACTIVE)).thenReturn(Optional.of(standardRoute));
            when(standardRouteRepository.findAssignmentsByRouteId(standardRoute.getId())).thenReturn(Set.of());

            StandardRouteResponseDTO result = standardRouteService.getStandardRouteStopPoints(standardRoute.getId(), GeneralStatus.ACTIVE);

            assertNotNull(result);

            assertEquals(result.id(), standardRouteResponseDTO.id());
        }

        @Test
        @DisplayName("Deve lançar exception quando a Rota Padrão não for encontrada")
        void shouldThrowExceptionWhenStandardRouteNotFound() {
            when(standardRouteRepository.findRouteBaseByIdAndStatus(standardRoute.getId(), GeneralStatus.ACTIVE)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class,() -> standardRouteService.getStandardRouteStopPoints(standardRoute.getId(), GeneralStatus.ACTIVE));

            verify(standardRouteRepository, times(1)).findRouteBaseByIdAndStatus(any(), any());
            verify(standardRouteRepository, never()).findAssignmentsByRouteId(any());
        }
    }

    @Nested
    class createStandardRoute {
        UUID customerId;
        StandardRouteRequestDTO standardRouteRequestDTOWithOutRouteStops;

        @BeforeEach
        void setUp() {
            Permissions perms = new Permissions("ROLE_ADMIN");
            user.setPermissions(List.of(perms));
            user.setCustomer(customer);

            standardRoute.setCustomer(customer);

            customerId = user.getCustomer().getId();

            standardRouteRequestDTOWithOutRouteStops = new StandardRouteRequestDTO(
                    "Rota Coração de Maria - Feira de Santana",
                    "Rota fictícia para testes saindo de Coração de Maria com destino a Feira de Santana.",
                    -12.233333,
                    -38.750000,
                    -12.266666,
                    -38.966666,
                    Set.of(TravelPeriod.MORNING),
                    Set.of()
            );
        }

        @Nested
        class successScenarios {
            @Test
            @DisplayName("Deve realizar a criação de uma nova rota padrão com sucesso")
            void shouldCreateNewStandardRouteAndReturnDtoWWhenDataIsValid() {
                List<UUID> routeStopsId = standardRouteRequestDTO.routeStops().stream().map(RouteStopAssignmentRequestDTO::routeStopId).toList();

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.existsByRouteNameAndCustomerId(standardRouteRequestDTO.routeName(), customerId))
                        .thenReturn(false);
                when(routeStopRepository.findAllById(routeStopsId)).thenReturn(List.of(routeStop));
                when(mapboxAPIService.calculateStandardRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                        .thenReturn(new RouteDetailsDTO(13.3, 5000.3, "encoded_geometry"));
                when(standardRouteRepository.save(any(StandardRoute.class))).thenAnswer(invocation -> {
                    StandardRoute savedRoute = invocation.getArgument(0);
                    savedRoute.setId(standardRouteResponseDTO.id());
                    return savedRoute;
                });

                StandardRouteResponseDTO result = standardRouteService.createStandardRoute(user.getEmail(), standardRouteRequestDTO);

                assertNotNull(result);
                assertEquals(GeneralStatus.ACTIVE, result.status());

                ArgumentCaptor<StandardRoute> captor = ArgumentCaptor.forClass(StandardRoute.class);
                verify(standardRouteRepository, times(1)).save(captor.capture());

                StandardRoute savedRoute = captor.getValue();

                assertEquals(standardRouteRequestDTO.routeName(), savedRoute.getRouteName());
                assertEquals("encoded_geometry", savedRoute.getStandardGeometry());
                assertEquals(GeneralStatus.ACTIVE, savedRoute.getStatus());
                assertEquals(customerId, savedRoute.getCustomer().getId());

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).existsByRouteNameAndCustomerId(eq(standardRouteRequestDTO.routeName()), eq(customerId));
                verify(routeStopRepository, times(1)).findAllById(anyList());
                verify(mapboxAPIService, times(1)).calculateStandardRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList());


            }
        }
        
        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando não achar o usuário autenticado no banco de dados")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> standardRouteService.createStandardRoute(user.getEmail(), standardRouteRequestDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));


            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception se o usuário não tiver a ROLE de ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRoleProvider")
            void shouldThrowExceptionWhenUserIsNotAdmin(String permission) {
                Permissions invalidPerms = new Permissions(permission);
                user.setPermissions(List.of(invalidPerms));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(NotAuthorizedException.class, () -> standardRouteService.createStandardRoute(user.getEmail(), standardRouteRequestDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));


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

                assertThrows(DomainValidationException.class, () -> standardRouteService.createStandardRoute(user.getEmail(), standardRouteRequestDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));


            }

            @Test
            void shouldThrowExceptionWhenAdminIsInactive() {
                user.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(InactiveAccountModificationException.class, () -> standardRouteService.createStandardRoute(user.getEmail(), standardRouteRequestDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));


            }

            @Test
            @DisplayName("Deve lançar exception quando não houver pontos de parada inseridos na rota padrão")
            void ShouldThrowDomainValidationExceptionWhenRouteStopsIsEmpty() {
                StandardRouteRequestDTO invalidStandardRouteRequestDTO = new StandardRouteRequestDTO(
                        "Rota Coração de Maria - Feira de Santana",
                        "Rota fictícia para testes saindo de Coração de Maria com destino a Feira de Santana.",
                        -12.233333,
                        -38.750000,
                        -12.266666,
                        -38.966666,
                        Set.of(TravelPeriod.MORNING),
                        Set.of()
                );

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(DomainValidationException.class, () -> standardRouteService.createStandardRoute(user.getEmail(), invalidStandardRouteRequestDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));


            }

            @Test
            @DisplayName("Deve lançar exception quando o RouteName já existir naquele Customer específico")
            void shouldThrowIllegalArgumentExceptionWhenRouteNameIsDuplicated() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.existsByRouteNameAndCustomerId(standardRouteRequestDTO.routeName(), customerId))
                        .thenReturn(true);

                assertThrows(IllegalArgumentException.class, () -> standardRouteService.createStandardRoute(user.getEmail(), standardRouteRequestDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).existsByRouteNameAndCustomerId(eq(standardRouteRequestDTO.routeName()), eq(customerId));

                verifyNoInteractions(mapboxAPIService);

            }

            @Test
            @DisplayName("Deve lançar exception quando a sequência dos pontos de parada forem null")
            void shouldThrowDomainValidationExceptionWhenStopSequenceHasNull() {
                StandardRouteRequestDTO invalidStandardRouteRequestDTO = new StandardRouteRequestDTO(
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
                                        null,
                                        false
                                )
                        )
                );

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(DomainValidationException.class, () -> standardRouteService.createStandardRoute(user.getEmail(), invalidStandardRouteRequestDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));


            }

            @Test
            @DisplayName("Deve lançar exception quando a sequência dos pontos de paradas forem repetidas (duplicadas)")
            void shouldThrowDomainValidationExceptionWhenStopSequenceIsDuplicated() {
                StandardRouteRequestDTO invalidStandardRouteRequestDTO = new StandardRouteRequestDTO(
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
                                ),
                                new RouteStopAssignmentRequestDTO(
                                        UUID.randomUUID(),
                                        2,
                                        false
                                ),
                                new RouteStopAssignmentRequestDTO(
                                        UUID.randomUUID(),
                                        1,
                                        true
                                )
                        )
                );

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(DomainValidationException.class, () -> standardRouteService.createStandardRoute(user.getEmail(), invalidStandardRouteRequestDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

            }

            @Test
            @DisplayName("Deve lançar exception quando a sequênca do routeStop for igual ou menor que zero")
            void shouldThrowDomainValidationExceptionWhenStopSequenceIsZeroOrNegative() {
                StandardRouteRequestDTO invalidStandardRouteRequestDTO = new StandardRouteRequestDTO(
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
                                ),
                                new RouteStopAssignmentRequestDTO(
                                        UUID.randomUUID(),
                                        2,
                                        false
                                ),
                                new RouteStopAssignmentRequestDTO(
                                        UUID.randomUUID(),
                                        0,
                                        true
                                )
                        )
                );

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(DomainValidationException.class, () -> standardRouteService.createStandardRoute(user.getEmail(), invalidStandardRouteRequestDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

            }

            @Test
            @DisplayName("Deve lançar exception quando houver duplicação de um mesmo RouteStop na rota padrão")
            void shouldThrowDomainValidationExceptionWhenRouteStopIdIsDuplicatedInRequest() {
                StandardRouteRequestDTO invalidStandardRouteRequestDTO = new StandardRouteRequestDTO(
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
                                ),
                                new RouteStopAssignmentRequestDTO(
                                        UUID.randomUUID(),
                                        2,
                                        false
                                ),
                                new RouteStopAssignmentRequestDTO(
                                        routeStop.getId(),
                                        3,
                                        true
                                )
                        )
                );

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(DomainValidationException.class, () -> standardRouteService.createStandardRoute(user.getEmail(), invalidStandardRouteRequestDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

            }

            @Test
            @DisplayName("Deve lançar exception quando nenhum routeStop for retornado pelo banco")
            void shouldThrowEntityNotFoundException_WhenNoRouteStopsFoundInRepository() {
                List<UUID> routeStopIds = standardRouteRequestDTO.routeStops().stream().map(RouteStopAssignmentRequestDTO::routeStopId).toList();

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findAllById(routeStopIds)).thenReturn(Collections.emptyList());

                assertThrows(EntityNotFoundException.class, () -> standardRouteService.createStandardRoute(user.getEmail(), standardRouteRequestDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

            }

            @Test
            @DisplayName("Deve lançar exception quando algum RouteStop retornado for INATIVO no sistema")
            void shouldThrowIllegalArgumentExceptionWhenAnyRouteStopIsInactive() {
                routeStop.setStatus(GeneralStatus.INACTIVE);

                List<UUID> routeStopIds = standardRouteRequestDTO.routeStops().stream().map(RouteStopAssignmentRequestDTO::routeStopId).toList();

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findAllById(routeStopIds)).thenReturn(List.of(routeStop));

                assertThrows(IllegalArgumentException.class, () -> standardRouteService.createStandardRoute(user.getEmail(), standardRouteRequestDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

            }

            @Test
            @DisplayName("Deve lançar exception quando o Customer do ponto de parada for difente do Customer da rota padrão")
            void shouldThrowExceptionWhenRouteStopBelongsToDifferentCustomer() {
                Customer differentCustomer = new Customer();
                routeStop.setCustomer(differentCustomer);
                List<UUID> routeStopIds = standardRouteRequestDTO.routeStops().stream().map(RouteStopAssignmentRequestDTO::routeStopId).toList();

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findAllById(routeStopIds)).thenReturn(List.of(routeStop));

                assertThrows(CustomerMismatchException.class, () -> standardRouteService.createStandardRoute(user.getEmail(), standardRouteRequestDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

            }
        }
    }

    @Nested
    class updateStandardRoute {
        StandardRouteUpdateDTO standardRouteUpdateDTO;

        @BeforeEach
        void setUp() {
            standardRouteUpdateDTO = new StandardRouteUpdateDTO("newRouteName", "newRouteDescription", Set.of(TravelPeriod.AFTERNOON), standardRoute.getOriginLatitude(), standardRoute.getOriginLongitude(), -12.19750, -38.96667);
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realizar o update de uma rota padrão existente com successo")
            void shouldUpdateAndReturnDTOWhenDataIsValid() {
                List<RouteStopAssignment> orderedAssignments = standardRoute.getRouteStopAssignments().stream()
                        .sorted(Comparator.comparing(RouteStopAssignment::getSequence)).toList();

                List<Point> waypoints = orderedAssignments.stream().map(route -> {
                    RouteStop eachRouteStop = route.getRouteStop();

                    return Point.fromLngLat(eachRouteStop.getLongitude(), eachRouteStop.getLatitude());
                }).toList();

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(mapboxAPIService.calculateStandardRoute(
                        eq(standardRouteUpdateDTO.originLongitude()),
                        eq(standardRouteUpdateDTO.originLatitude()),
                        eq(standardRouteUpdateDTO.destinationLongitude()),
                        eq(standardRouteUpdateDTO.destinationLatitude()),
                        eq(waypoints)))
                        .thenReturn(new RouteDetailsDTO(13.4, 5000.3,"new_encoded_geometry"));
                when(standardRouteRepository.save(eq(standardRoute))).thenReturn(standardRoute);

                StandardRouteResponseDTO result = standardRouteService.updateStandardRoute(standardRoute.getId(), user.getEmail(), standardRouteUpdateDTO);

                assertNotNull(result);

                assertNotNull(result.updatedAt());

                ArgumentCaptor<StandardRoute> standardRouteArgCaptor = ArgumentCaptor.forClass(StandardRoute.class);

                verify(standardRouteRepository, times(1)).save(standardRouteArgCaptor.capture());

                StandardRoute savedValue = standardRouteArgCaptor.getValue();

                // mudam com base no DTO
                assertEquals(savedValue.getRouteName(), result.routeName());
                assertEquals(savedValue.getRouteDescription(), result.routeDescription());
                assertEquals(savedValue.getTravelPeriods(), result.travelPeriods());
                assertEquals(savedValue.getDestinationLatitude(), result.destinationLatitude());
                assertEquals(savedValue.getDestinationLongitude(), result.destinationLongitude());

                // mudam pela resposta da API
                assertEquals(savedValue.getStandardGeometry(), result.standardGeometry());

                // não devem mudar
                assertEquals(standardRoute.getOriginLatitude(), result.originLatitude());
                assertEquals(standardRoute.getOriginLongitude(), result.originLongitude());
            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o user não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> standardRouteService.updateStandardRoute(standardRoute.getId(), user.getEmail(), standardRouteUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(standardRouteRepository, routeStopRepository, mapboxAPIService);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception se o usuário não tiver a ROLE de ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRoleProvider")
            void shouldThrowExceptionWhenUserIsNotAdmin(String permission) {
                Permissions invalidPerms = new Permissions(permission);
                user.setPermissions(List.of(invalidPerms));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(NotAuthorizedException.class, () -> standardRouteService.updateStandardRoute(standardRoute.getId(), user.getEmail(), standardRouteUpdateDTO));

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

                assertThrows(DomainValidationException.class, () -> standardRouteService.updateStandardRoute(standardRoute.getId(), user.getEmail(), standardRouteUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(standardRouteRepository, routeStopRepository, mapboxAPIService);
            }

            @Test
            void shouldThrowExceptionWhenAdminIsInactive() {
                user.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(InactiveAccountModificationException.class, () -> standardRouteService.updateStandardRoute(standardRoute.getId(), user.getEmail(), standardRouteUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(standardRouteRepository, routeStopRepository, mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando o Customer da rota padrão for diferente do Customer do usuário autenticado")
            void shouldThrowExceptionWhenRouteBelongsToDifferentCustomer() {
                Customer differentCustomer = new Customer();
                standardRoute.setCustomer(differentCustomer);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(CustomerMismatchException.class, () -> standardRouteService.updateStandardRoute(standardRoute.getId(), user.getEmail(), standardRouteUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(routeStopRepository, mapboxAPIService);

            }

            @Test
            @DisplayName("Deve lançar exception quando o nome da rota já existir nesse customer em específico")
            void shouldThrowIllegalArgumentExceptionWhenRouteNameIsDuplicated() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.existsByRouteNameAndCustomerIdAndIdNot(standardRouteUpdateDTO.routeName(), customer.getId(), standardRoute.getId()))
                        .thenReturn(true);

                assertThrows(IllegalArgumentException.class, () -> standardRouteService.updateStandardRoute(standardRoute.getId(), user.getEmail(), standardRouteUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).existsByRouteNameAndCustomerIdAndIdNot(
                        eq(standardRouteUpdateDTO.routeName()),
                        eq(customer.getId()),
                        eq(standardRoute.getId()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(routeStopRepository, mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando a rota padrão não for encontrada")
            void shouldThrowEntityNotFoundExceptionWhenStandardRouteNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> standardRouteService.updateStandardRoute(standardRoute.getId(), user.getEmail(), standardRouteUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(routeStopRepository, mapboxAPIService);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando as coordenadas de origem estiverem incompletas ou inválidas")
            @MethodSource("invalidOriginCoordinatesProvider")
            void shouldThrowNoSuchCoordinatesWhenOriginCoordinatesAreIncomplete(StandardRouteUpdateDTO updateDTOWithInvalidOriginCoords) {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(NoSuchCoordinates.class, () -> standardRouteService.updateStandardRoute(standardRoute.getId(), user.getEmail(), updateDTOWithInvalidOriginCoords));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(routeStopRepository, mapboxAPIService);
            }

            public static Stream<Arguments> invalidOriginCoordinatesProvider() {
                return Stream.of(
                        Arguments.of(new StandardRouteUpdateDTO("Exemplo RouteName", "Exemplo routeDescription", Set.of(TravelPeriod.MORNING), null, -32.2342, -11.342, -40.232)),
                        Arguments.of(new StandardRouteUpdateDTO("Exemplo RouteName", "Exemplo routeDescription", Set.of(TravelPeriod.MORNING), -9.242, null, -11.342, -40.232))
                );
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando as coordenadas de destino estiverem incompletas ou inválidas")
            @MethodSource("invalidDestinationCoordinatesProvider")
            void shouldThrowNoSuchCoordinatesWhenDestinationCoordinatesAreIncomplete(StandardRouteUpdateDTO updateDTOWithInvalidDestinationCoords) {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(NoSuchCoordinates.class, () -> standardRouteService.updateStandardRoute(standardRoute.getId(), user.getEmail(), updateDTOWithInvalidDestinationCoords));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(routeStopRepository, mapboxAPIService);
            }

            public static Stream<Arguments> invalidDestinationCoordinatesProvider() {
                return Stream.of(
                        Arguments.of(new StandardRouteUpdateDTO("Exemplo RouteName", "Exemplo routeDescription", Set.of(TravelPeriod.MORNING), -9.242, -32.2342, null, -40.232)),
                        Arguments.of(new StandardRouteUpdateDTO("Exemplo RouteName", "Exemplo routeDescription", Set.of(TravelPeriod.MORNING), -9.242, -32.2342, -11.342, null))
                );
            }

            @Test
            @DisplayName("Deve lançar exception quando o mapbox falhar durante o recalculo da rota")
            void shouldThrowRecalculateEtaExceptionWhenMapboxFails() {
                List<RouteStopAssignment> orderedAssignments = standardRoute.getRouteStopAssignments().stream()
                        .sorted(Comparator.comparing(RouteStopAssignment::getSequence)).toList();

                List<Point> waypoints = orderedAssignments.stream().map(route -> {
                    RouteStop eachRouteStop = route.getRouteStop();

                    return Point.fromLngLat(eachRouteStop.getLongitude(), eachRouteStop.getLatitude());
                }).toList();

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(mapboxAPIService.calculateStandardRoute(
                        eq(standardRouteUpdateDTO.originLongitude()),
                        eq(standardRouteUpdateDTO.originLatitude()),
                        eq(standardRouteUpdateDTO.destinationLongitude()),
                        eq(standardRouteUpdateDTO.destinationLatitude()),
                        eq(waypoints)))
                        .thenThrow(new RecalculateEtaException("Falha durante a execução de chamada da API externa"));

                assertThrows(RecalculateEtaException.class, () -> standardRouteService.updateStandardRoute(standardRoute.getId(), user.getEmail(), standardRouteUpdateDTO));

                verify(standardRouteRepository, never()).save(any());
            }
        }
    }

    @Nested
    class updateRouteStopPoints {
        StandardRouteStopsUpdateDTO standardRouteStopsUpdateDTO;

        @BeforeEach
        void setUp() {
            standardRouteStopsUpdateDTO = new StandardRouteStopsUpdateDTO(Set.of(new RouteStopAssignmentRequestDTO(routeStop.getId(), 1, false)));
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realizar o update do Route Stop Point com sucesso")
            void shouldUpdateRouteStopPointsAndReturnDtoWhenDataIsValid() {
                List<UUID> routeStopIds = standardRouteStopsUpdateDTO.routeStops().stream().map(RouteStopAssignmentRequestDTO::routeStopId).toList();

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findAllById(routeStopIds)).thenReturn(List.of(routeStop));
                when(mapboxAPIService.calculateStandardRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                        .thenReturn(new RouteDetailsDTO(14.4, 5000.3, "encoded_geometry"));
                when(standardRouteRepository.save(standardRoute)).thenReturn(standardRoute);

                StandardRouteResponseDTO result = standardRouteService.updateRouteStopPoints(standardRoute.getId(), user.getEmail(), standardRouteStopsUpdateDTO);

                assertNotNull(result);

                ArgumentCaptor<StandardRoute> standardRouteArgCaptor = ArgumentCaptor.forClass(StandardRoute.class);

                verify(standardRouteRepository, times(1)).save(standardRouteArgCaptor.capture());

                StandardRoute storageValue = standardRouteArgCaptor.getValue();

                assertNotNull(result.updatedAt());

                assertEquals(storageValue.getStandardGeometry(), result.standardGeometry());
                assertEquals(storageValue.getRouteStopAssignments().stream().map(id -> id.getRouteStop().getId()).toList(),
                        result.routeStopAssignments().stream().map(RouteStopAssignmentResponseDTO::routeStopId).toList());
            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o usuário não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> standardRouteService.updateRouteStopPoints(standardRoute.getId(), user.getEmail(), standardRouteStopsUpdateDTO));

                verifyNoInteractions(standardRouteRepository, standardRouteRepository, mapboxAPIService);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception se o usuário não tiver a ROLE de ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRoleProvider")
            void shouldThrowExceptionWhenUserIsNotAdmin(String permission) {
                Permissions invalidPerms = new Permissions(permission);
                user.setPermissions(List.of(invalidPerms));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(NotAuthorizedException.class, () -> standardRouteService.updateRouteStopPoints(standardRoute.getId(), user.getEmail(), standardRouteStopsUpdateDTO));

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

                assertThrows(DomainValidationException.class, () -> standardRouteService.updateRouteStopPoints(standardRoute.getId(), user.getEmail(), standardRouteStopsUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(standardRouteRepository, routeStopRepository, mapboxAPIService);
            }

            @Test
            void shouldThrowExceptionWhenAdminIsInactive() {
                user.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(InactiveAccountModificationException.class, () -> standardRouteService.updateRouteStopPoints(standardRoute.getId(), user.getEmail(), standardRouteStopsUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(standardRouteRepository, routeStopRepository, mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando o Customer da rota padrão for diferente do Customer do usuário autenticado")
            void shouldThrowExceptionWhenRouteBelongsToDifferentCustomer() {
                Customer differentCustomer = new Customer();
                standardRoute.setCustomer(differentCustomer);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(CustomerMismatchException.class, () -> standardRouteService.updateRouteStopPoints(standardRoute.getId(), user.getEmail(), standardRouteStopsUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(routeStopRepository, mapboxAPIService);

            }

            @Test
            @DisplayName("Deve lançar exception quando a Rota Padrão não for encontrada")
            void shouldThrowEntityNotFoundExceptionWhenStandardRouteNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> standardRouteService.updateRouteStopPoints(standardRoute.getId(), user.getEmail(), standardRouteStopsUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(routeStopRepository, mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando não encontrar o(s) RouteStop(s)")
            void shouldThrowEntityNotFoundExceptionWhenNoRouteStopsFound() {
                List<UUID> routeStopIds = standardRouteStopsUpdateDTO.routeStops().stream().map(RouteStopAssignmentRequestDTO::routeStopId).toList();

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findAllById(routeStopIds)).thenReturn(List.of());

                assertThrows(EntityNotFoundException.class, () -> standardRouteService.updateRouteStopPoints(standardRoute.getId(), user.getEmail(), standardRouteStopsUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));
                verify(routeStopRepository, times(1)).findAllById(eq(routeStopIds));

                verifyNoMoreInteractions(userRepository);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando RouteStop é inválida ou vazio")
            @MethodSource("invalidStandardRouteStopsUpdateDTOProvider")
            void shouldThrowDomainValidationExceptionWhenRouteStopsIsInvalidOrEmpty(StandardRouteStopsUpdateDTO invalidStandardRouteStopsUpdateDTO) {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(DomainValidationException.class, () -> standardRouteService.updateRouteStopPoints(standardRoute.getId(), user.getEmail(), invalidStandardRouteStopsUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));

                verifyNoMoreInteractions(userRepository);
            }

            public static Stream<Arguments> invalidStandardRouteStopsUpdateDTOProvider() {
                return Stream.of(
                        Arguments.of(new StandardRouteStopsUpdateDTO(Set.of(new RouteStopAssignmentRequestDTO(null, null, true)))),
                        Arguments.of( (StandardRouteStopsUpdateDTO) null)
                );
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando a List ordenada pela Sequence estiver com dados inválidos")
            @MethodSource("invalidSequenceProvider")
            void shouldThrowDomainValidationExceptionWhenStopSequenceIsNull(StandardRouteStopsUpdateDTO invalidStandardRouteStopsUpdateDTO) {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(DomainValidationException.class, () -> standardRouteService.updateRouteStopPoints(standardRoute.getId(), user.getEmail(), invalidStandardRouteStopsUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));

                verifyNoMoreInteractions(userRepository);
            }

            public static Stream<Arguments> invalidSequenceProvider() {
                return Stream.of(
                        Arguments.of(new StandardRouteStopsUpdateDTO(Set.of(new RouteStopAssignmentRequestDTO(null, null, true)))),
                        Arguments.of(new StandardRouteStopsUpdateDTO(Set.of(new RouteStopAssignmentRequestDTO(null, 0, true)))),
                        Arguments.of(new StandardRouteStopsUpdateDTO(Set.of(new RouteStopAssignmentRequestDTO(null, 1, true)))),
                        Arguments.of(new StandardRouteStopsUpdateDTO(Set.of(new RouteStopAssignmentRequestDTO(null, 1, true))))

                );
            }

            @Test
            @DisplayName("Deve lançar exception quando o RouteStop possuir ID null")
            void shouldThrowDomainValidationExceptionWhenRouteStopIdIsNull() {
                StandardRouteStopsUpdateDTO invalidStandardRouteStopsUpdateDTO = new StandardRouteStopsUpdateDTO(Set.of(new RouteStopAssignmentRequestDTO(null, 1, false)));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(DomainValidationException.class, () -> standardRouteService.updateRouteStopPoints(standardRoute.getId(), user.getEmail(), invalidStandardRouteStopsUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(routeStopRepository, mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando o mesmo RouteStop aparecer repetidamente")
            void shouldThrowDomainValidationExceptionWhenRouteStopIdIsDuplicated() {
                StandardRouteStopsUpdateDTO invalidStandardRouteStopsUpdateDTO = new StandardRouteStopsUpdateDTO(Set.of(new RouteStopAssignmentRequestDTO(routeStop.getId(),1, false), new RouteStopAssignmentRequestDTO(routeStop.getId(), 2, false)));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                assertThrows(DomainValidationException.class, () -> standardRouteService.updateRouteStopPoints(standardRoute.getId(), user.getEmail(), invalidStandardRouteStopsUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(routeStopRepository, mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar excepton quando houver tentativa de vínculo de RouteStop inativo")
            void shouldThrowIllegalArgumentExceptionWhenRouteStopIsInactive() {
                routeStop.setStatus(GeneralStatus.INACTIVE);

                List<UUID> routeStopIds = standardRouteStopsUpdateDTO.routeStops().stream().map(RouteStopAssignmentRequestDTO::routeStopId).toList();

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findAllById(routeStopIds)).thenReturn(List.of(routeStop));

                assertThrows(IllegalArgumentException.class, () -> standardRouteService.updateRouteStopPoints(standardRoute.getId(), user.getEmail(), standardRouteStopsUpdateDTO));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(standardRouteRepository, times(1)).findById(eq(standardRoute.getId()));
                verify(routeStopRepository, times(1)).findAllById(eq(routeStopIds));

                verifyNoMoreInteractions(userRepository);
            }
        }
    }


}