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

/*    @Spy
    private StandardRouteRequestMapper standardRouteRequestMapper;
    @Spy
    private StandardRouteResponseMapper standardRouteResponseMapper;*/

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

        standardRoute = new StandardRoute(UUID.randomUUID(), "Rota Universitária - Linha Leste", "Trajeto diário de transporte universitário conectando pontos de embarque ao campus central.", -12.2333, -38.7500, -12.2670, -38.9670, "a~|~Fkf~vO|@_@eA_@m@g@_@y@e@...", TravelPeriod.MORNING, customer, GeneralStatus.ACTIVE, Instant.parse("2026-08-10T12:00:00Z"), Instant.parse("2026-08-10T12:00:00Z"));

        routeStop = new RouteStop(UUID.randomUUID(), "RouteStopName", "RouteStop Description", -45.324, -11.342, customer, GeneralStatus.ACTIVE);

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
            when(standardRouteRepository.findRouteBaseByIdAndStatus(standardRoute.getId(), GeneralStatus.ACTIVE)).thenReturn(Optional.of(standardRouteResponseDTO));
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
                    TravelPeriod.MORNING,
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
                        TravelPeriod.MORNING,
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
                        TravelPeriod.MORNING,
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
                        TravelPeriod.MORNING,
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
                        TravelPeriod.MORNING,
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
                        TravelPeriod.MORNING,
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
            standardRouteUpdateDTO = new StandardRouteUpdateDTO("newRouteName", "newRouteDescription", TravelPeriod.AFTERNOON, standardRoute.getOriginLatitude(), standardRoute.getOriginLongitude(), -12.19750, -38.96667);
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
                assertEquals(savedValue.getTravelPeriod(), result.travelPeriod());
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
                        Arguments.of(new StandardRouteUpdateDTO("Exemplo RouteName", "Exemplo routeDescription", TravelPeriod.MORNING, null, -32.2342, -11.342, -40.232)),
                        Arguments.of(new StandardRouteUpdateDTO("Exemplo RouteName", "Exemplo routeDescription", TravelPeriod.MORNING, -9.242, null, -11.342, -40.232))
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
                        Arguments.of(new StandardRouteUpdateDTO("Exemplo RouteName", "Exemplo routeDescription", TravelPeriod.MORNING, -9.242, -32.2342, null, -40.232)),
                        Arguments.of(new StandardRouteUpdateDTO("Exemplo RouteName", "Exemplo routeDescription", TravelPeriod.MORNING, -9.242, -32.2342, -11.342, null))
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

                standardRouteService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE);

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

                assertThrows(EntityNotFoundException.class, () -> standardRouteService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verifyNoInteractions(standardRouteRepository, standardRouteRepository, mapboxAPIService);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception se o usuário não tiver a ROLE de ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRoleProvider")
            void shouldThrowExceptionWhenUserIsNotAdmin(String permission) {
                Permissions invalidPerms = new Permissions(permission);
                user.setPermissions(List.of(invalidPerms));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(NotAuthorizedException.class, () -> standardRouteService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

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

                assertThrows(DomainValidationException.class, () -> standardRouteService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(standardRouteRepository, routeStopRepository, mapboxAPIService);
            }

            @Test
            void shouldThrowExceptionWhenAdminIsInactive() {
                user.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(InactiveAccountModificationException.class, () -> standardRouteService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

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

                assertThrows(CustomerMismatchException.class, () -> standardRouteService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

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

                assertThrows(EntityNotFoundException.class, () -> standardRouteService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(routeStopRepository, userRepository);

                verifyNoInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando o routeStop ja possuir o status do parâmetro")
            void shouldThrowDuplicateResourceExceptionWhenStatusIsSameAsCurrent() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(DuplicateResourceException.class, () -> standardRouteService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.ACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(routeStopRepository, never()).save(any());

                verifyNoMoreInteractions(routeStopRepository, userRepository);

                verifyNoInteractions(mapboxAPIService);
            }
        }
    }

    @Nested
    class associateRouteStopWithStandardRoute {
        int sequence = 1;
        boolean isOptionalSpot = false;

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realzar a associação de um RouteStop com o StandardRoute, criando um RouteStopAssignment com successo")
            void shouldAssociateRouteStopWithStandardRouteWhenDataIsValid() {
                standardRoute.setRouteStopAssignments(new ArrayList<>());

                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                RouteDetailsDTO routeDetailsDTO = mock(RouteDetailsDTO.class);
                when(routeDetailsDTO.geometry()).thenReturn("mocked-geometry");

                when(mapboxAPIService.calculateStandardRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                        .thenReturn(routeDetailsDTO);

                standardRouteService.associateRouteStopWithStandardRoute(standardRoute.getId(), routeStop.getId(), sequence, isOptionalSpot);

                verify(standardRouteRepository).findById(standardRoute.getId());
                verify(routeStopRepository).findById(routeStop.getId());
                verify(mapboxAPIService).calculateStandardRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList());

                ArgumentCaptor<StandardRoute> captor = ArgumentCaptor.forClass(StandardRoute.class);
                verify(standardRouteRepository).save(captor.capture());

                StandardRoute savedRoute = captor.getValue();
                assertNotNull(savedRoute.getRouteStopAssignments());
                assertEquals("mocked-geometry", savedRoute.getStandardGeometry());

                boolean hasNewAssignment = savedRoute.getRouteStopAssignments().stream()
                        .anyMatch(assignment ->
                                assignment.getRouteStop().getId().equals(routeStop.getId()) &&
                                        assignment.getSequence() == sequence &&
                                        assignment.isOptionalSpot() == isOptionalSpot
                        );

                assertTrue(hasNewAssignment);
            }
        }

        @Nested
        class failureScenarios {

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando a sequência inserida for zero ou negativa")
            @MethodSource("provideInvalidSequences")
            void shouldThrowIllegalArgumentExceptionWhenSequenceIsZeroOrNegative(int sequence) {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                        standardRouteService.associateRouteStopWithStandardRoute(standardRoute.getId(), routeStop.getId(), sequence, false)
                );

                assertTrue(exception.getMessage().contains("A ordem de sequencia da rota deve ser maior que zero:"));

                verifyNoInteractions(standardRouteRepository);
                verifyNoInteractions(routeStopRepository);
            }

            public static Stream<Arguments> provideInvalidSequences() {
                return Stream.of(
                        Arguments.of(0),
                        Arguments.of(-1),
                        Arguments.of(-10)
                );
            }

            @Test
            @DisplayName("Deve lançar exception quando não encontar a Rota Padrão")
            void shouldThrowEntityNotFoundExceptionWhenStandardRouteNotFound() {
                UUID standardRouteId = UUID.randomUUID();
                UUID routeStopId = UUID.randomUUID();

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.empty());

                EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                        standardRouteService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, 1, false)
                );

                assertEquals("Rota padrão não encontrada", exception.getMessage());

                verify(standardRouteRepository).findById(standardRouteId);
                verifyNoInteractions(routeStopRepository);
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }

            @Test
            @DisplayName("Deve lançar exception quando não encontrar o Route Stop")
            void shouldThrowEntityNotFoundExceptionWhenRouteStopNotFound() {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopId = UUID.randomUUID();

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStopId)).thenReturn(Optional.empty());

                EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                        standardRouteService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, 1, false)
                );

                assertEquals("Ponto de parada não encontrado", exception.getMessage());

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findById(routeStopId);
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }

            @Test
            @DisplayName("Deve lançar exception quando os Customers forem diferentes")
            void shouldThrowExceptionWhenRouteAndRouteStopBelongToDifferentCustomers() {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopId = routeStop.getId();

                Customer differentCustomer = new Customer();
                differentCustomer.setId(UUID.randomUUID());
                routeStop.setCustomer(differentCustomer);

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStopId)).thenReturn(Optional.of(routeStop));

                assertThrows(CustomerMismatchException.class, () ->
                        standardRouteService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, 1, false)
                );

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findById(routeStopId);
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando a Rota Padrão ou o Ponto de Parada estiver inativo")
            @MethodSource("provideInactiveStatusCombinations")
            void shouldThrowIllegalArgumentExceptionWhenRouteOrRouteStopIsInactive(GeneralStatus routeStatus, GeneralStatus routeStopStatus) {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopId = routeStop.getId();

                standardRoute.setStatus(routeStatus);
                routeStop.setStatus(routeStopStatus);

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStopId)).thenReturn(Optional.of(routeStop));

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                        standardRouteService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, 1, false)
                );

                assertEquals("A Rota padrão ou o poto de parada está inativo", exception.getMessage());

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findById(routeStopId);
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }

            public static Stream<Arguments> provideInactiveStatusCombinations() {
                return Stream.of(
                        Arguments.of(GeneralStatus.INACTIVE, GeneralStatus.ACTIVE),
                        Arguments.of(GeneralStatus.ACTIVE, GeneralStatus.INACTIVE),
                        Arguments.of(GeneralStatus.INACTIVE, GeneralStatus.INACTIVE)
                );
            }

            @Test
            @DisplayName("Deve lançar exception quando o Route Stop já estiver associado à rota")
            void shouldThrowIllegalArgumentExceptionWhenRouteStopIsAlreadyAssociated() {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopId = routeStop.getId();

                standardRoute.setRouteStopAssignments(new ArrayList<>());

                RouteStopAssignment existingAssignment = new RouteStopAssignment();
                existingAssignment.setRouteStop(routeStop);
                existingAssignment.setSequence(1);
                standardRoute.getRouteStopAssignments().add(existingAssignment);

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStopId)).thenReturn(Optional.of(routeStop));

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                        standardRouteService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, 2, false)
                );

                assertTrue(exception.getMessage().contains("Esse Ponto de Parada já está vinculada à rota:"));
                assertTrue(exception.getMessage().contains(standardRouteId.toString()));

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findById(routeStopId);
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }

            @Test
            @DisplayName("Deve lançar exception quando a sequência inserida já estiver em uso por outro Ponto de Parada")
            void shouldThrowIllegalArgumentExceptionWhenSequenceIsAlreadyInUse() {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopId = routeStop.getId();
                int sequenceToTest = 1;

                standardRoute.setRouteStopAssignments(new ArrayList<>());

                RouteStopAssignment existingAssignment = new RouteStopAssignment();

                RouteStop anotherRouteStop = new RouteStop();
                anotherRouteStop.setId(UUID.randomUUID());
                anotherRouteStop.setCustomer(customer);

                existingAssignment.setRouteStop(anotherRouteStop);
                existingAssignment.setSequence(sequenceToTest);
                standardRoute.getRouteStopAssignments().add(existingAssignment);

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStopId)).thenReturn(Optional.of(routeStop));

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                        standardRouteService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, sequenceToTest, false)
                );

                assertTrue(exception.getMessage().contains("Já existe um Ponto de Parada nessa ordem de sequência:"));
                assertTrue(exception.getMessage().contains(String.valueOf(sequenceToTest)));

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findById(routeStopId);
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }
        }

    }

    @Nested
    class removeRouteStopWithStandardRoute {

        @Nested
        class successScenarios {
            @Test
            @DisplayName("Deve remover o RouteStop da Rota Padrão e realizar o recálculo da Rota Padrão com base nos RouteStops restantes com sucesso")
            void shouldRemoveRouteStopAndRecalculateGeometryWhenAssignmentsRemain() {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopIdToRemove = UUID.randomUUID();
                UUID routeStopIdToKeep1 = UUID.randomUUID();
                UUID routeStopIdToKeep2 = UUID.randomUUID();

                // 3 routeStops (1 remove, 2 keep)
                RouteStop routeStopToRemove = new RouteStop();
                routeStopToRemove.setLatitude(-11.234);
                routeStopToRemove.setLongitude(-38.232);
                routeStopToRemove.setId(routeStopIdToRemove);
                routeStopToRemove.setCustomer(customer);
                routeStopToRemove.setStatus(GeneralStatus.ACTIVE);

                RouteStop routeStopToKeep1 = new RouteStop();
                routeStopToKeep1.setLatitude(-9.231);
                routeStopToKeep1.setLongitude(-23.234);
                routeStopToKeep1.setId(routeStopIdToKeep1);
                routeStopToKeep1.setCustomer(customer);

                RouteStop routeStopToKeep2 = new RouteStop();
                routeStopToKeep2.setLatitude(-19.232);
                routeStopToKeep2.setLongitude(-43.211);
                routeStopToKeep2.setId(routeStopIdToKeep2);
                routeStopToKeep2.setCustomer(customer);

                // 3 routeStopAssignments
                RouteStopAssignment assignment1 = new RouteStopAssignment();
                assignment1.setRouteStop(routeStopToKeep1);
                assignment1.setSequence(1);

                RouteStopAssignment assignment2 = new RouteStopAssignment();
                assignment2.setRouteStop(routeStopToRemove);
                assignment2.setSequence(2);

                RouteStopAssignment assignment3 = new RouteStopAssignment();
                assignment3.setRouteStop(routeStopToKeep2);
                assignment3.setSequence(3);

                standardRoute.setRouteStopAssignments(new ArrayList<>(List.of(assignment1, assignment2, assignment3)));
                standardRoute.setStatus(GeneralStatus.ACTIVE);

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStopIdToRemove)).thenReturn(Optional.of(routeStopToRemove));

                RouteDetailsDTO routeDetailsDTO = mock(RouteDetailsDTO.class);
                when(routeDetailsDTO.geometry()).thenReturn("recalculated-geometry");
                when(mapboxAPIService.calculateStandardRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList())).thenReturn(routeDetailsDTO);

                standardRouteService.removeRouteStopWithStandardRoute(standardRouteId, routeStopIdToRemove);

                // Asserts
                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findById(routeStopIdToRemove);
                verify(mapboxAPIService).calculateStandardRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList());

                ArgumentCaptor<StandardRoute> captor = ArgumentCaptor.forClass(StandardRoute.class);
                verify(standardRouteRepository).save(captor.capture());

                StandardRoute savedRoute = captor.getValue();
                assertEquals("recalculated-geometry", savedRoute.getStandardGeometry());

                List<RouteStopAssignment> savedAssignments = savedRoute.getRouteStopAssignments();
                assertEquals(2, savedAssignments.size());

                boolean removedExists = savedAssignments.stream()
                        .anyMatch(a -> a.getRouteStop().getId().equals(routeStopIdToRemove));
                assertFalse(removedExists);

                List<Integer> sequences = savedAssignments.stream()
                        .map(RouteStopAssignment::getSequence)
                        .sorted()
                        .toList();
                assertEquals(List.of(1, 2), sequences);
            }

            @Test
            @DisplayName("Deve remover o RouteStop da Rota Padrão e salvar sem realizar recálculo quando não houver mais nenhum RouteStop na Rota Padrão")
            void shouldRemoveRouteStopAndSaveWithoutRecalculatingWhenNoAssignmentsRemain() {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopIdToRemove = routeStop.getId();

                RouteStopAssignment assignment = new RouteStopAssignment();
                assignment.setRouteStop(routeStop);
                assignment.setSequence(1);

                standardRoute.setRouteStopAssignments(new ArrayList<>(List.of(assignment)));
                standardRoute.setStatus(GeneralStatus.ACTIVE);
                routeStop.setStatus(GeneralStatus.ACTIVE);

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStopIdToRemove)).thenReturn(Optional.of(routeStop));

                standardRouteService.removeRouteStopWithStandardRoute(standardRouteId, routeStopIdToRemove);

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findById(routeStopIdToRemove);
                verifyNoInteractions(mapboxAPIService);

                ArgumentCaptor<StandardRoute> captor = ArgumentCaptor.forClass(StandardRoute.class);
                verify(standardRouteRepository).save(captor.capture());

                StandardRoute savedRoute = captor.getValue();
                assertTrue(savedRoute.getRouteStopAssignments().isEmpty());
            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando não encontrar a Rota Padrão")
            void shouldThrowEntityNotFoundExceptionWhenStandardRouteNotFound() {
                UUID standardRouteId = UUID.randomUUID();
                UUID routeStopId = UUID.randomUUID();

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.empty());

                EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                        standardRouteService.removeRouteStopWithStandardRoute(standardRouteId, routeStopId)
                );

                assertEquals("Rota padrão não encontrada", exception.getMessage());

                verify(standardRouteRepository).findById(standardRouteId);
                verifyNoInteractions(routeStopRepository);
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }

            @Test
            @DisplayName("Deve lançar exception quando não encontrar o Ponto de Parada")
            void shouldThrowEntityNotFoundExceptionWhenRouteStopNotFound() {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopId = UUID.randomUUID();

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStopId)).thenReturn(Optional.empty());

                EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                        standardRouteService.removeRouteStopWithStandardRoute(standardRouteId, routeStopId)
                );

                assertEquals("Ponto de parada não encontrado", exception.getMessage());

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findById(routeStopId);
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }

            @Test
            @DisplayName("Deve lançar exception quando a Rota Padão e o Ponto de Parada for de Customers diferentes")
            void shouldThrowExceptionWhenRouteAndRouteStopBelongToDifferentCustomers() {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopId = routeStop.getId();

                Customer differentCustomer = new Customer();
                differentCustomer.setId(UUID.randomUUID());
                routeStop.setCustomer(differentCustomer);

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStopId)).thenReturn(Optional.of(routeStop));

                assertThrows(RuntimeException.class, () ->
                        standardRouteService.removeRouteStopWithStandardRoute(standardRouteId, routeStopId)
                );

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findById(routeStopId);
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando a Rota Padrão ou o Ponto de Parda for Inativo")
            @MethodSource("provideInactiveStatusCombinations")
            void shouldThrowIllegalArgumentExceptionWhenRouteOrRouteStopIsInactive(GeneralStatus routeStatus, GeneralStatus routeStopStatus) {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopId = routeStop.getId();

                standardRoute.setStatus(routeStatus);
                routeStop.setStatus(routeStopStatus);

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findById(routeStopId)).thenReturn(Optional.of(routeStop));

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                        standardRouteService.removeRouteStopWithStandardRoute(standardRouteId, routeStopId)
                );

                assertEquals("A Rota padrão ou o poto de parada está inativo", exception.getMessage());

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findById(routeStopId);
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }

            public static Stream<Arguments> provideInactiveStatusCombinations() {
                return Stream.of(
                        Arguments.of(GeneralStatus.INACTIVE, GeneralStatus.ACTIVE),
                        Arguments.of(GeneralStatus.ACTIVE, GeneralStatus.INACTIVE),
                        Arguments.of(GeneralStatus.INACTIVE, GeneralStatus.INACTIVE)
                );
            }
        }
    }

    @Nested
    class reorderRouteStops {

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realizar a reordenação dos Route Stops de uma Rota padrão com sucesso")
            void shouldReorderRouteStopsAndReturnDtoWhenDataIsValid() {
                UUID standardRouteId = standardRoute.getId();

                RouteStop routeStop1 = new RouteStop(UUID.randomUUID(), "Stop 1", "Desc", -12.0, -38.0, customer, GeneralStatus.ACTIVE);
                RouteStop routeStop2 = new RouteStop(UUID.randomUUID(), "Stop 2", "Desc", -12.1, -38.1, customer, GeneralStatus.ACTIVE);

                RouteStopAssignment assignment1 = new RouteStopAssignment();
                assignment1.setRouteStop(routeStop1);
                assignment1.setSequence(1);

                RouteStopAssignment assignment2 = new RouteStopAssignment();
                assignment2.setRouteStop(routeStop2);
                assignment2.setSequence(2);

                standardRoute.setRouteStopAssignments(new ArrayList<>(List.of(assignment1, assignment2)));
                standardRoute.setStatus(GeneralStatus.ACTIVE);

                // faz a reorder: routestop 2 agora é 1
                List<RouteStopReorderRequestDTO> routeStopsReorder = List.of(
                        new RouteStopReorderRequestDTO(routeStop2.getId(), 1),
                        new RouteStopReorderRequestDTO(routeStop1.getId(), 2)
                );

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findAllById(anyCollection())).thenReturn(List.of(routeStop1, routeStop2));

                RouteDetailsDTO routeDetailsDTO = mock(RouteDetailsDTO.class);
                when(routeDetailsDTO.geometry()).thenReturn("new-geometry");
                when(mapboxAPIService.calculateStandardRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList())).thenReturn(routeDetailsDTO);

                when(standardRouteRepository.save(any(StandardRoute.class))).thenReturn(standardRoute);

                StandardRouteResponseDTO result = standardRouteService.reorderRouteStops(standardRouteId, routeStopsReorder);

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findAllById(anyCollection());
                verify(mapboxAPIService).calculateStandardRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList());

                ArgumentCaptor<StandardRoute> captor = ArgumentCaptor.forClass(StandardRoute.class);
                verify(standardRouteRepository).save(captor.capture());

                StandardRoute savedRoute = captor.getValue();
                assertEquals("new-geometry", savedRoute.getStandardGeometry());

                Map<UUID, Integer> finalSequences = savedRoute.getRouteStopAssignments().stream()
                        .collect(Collectors.toMap(a -> a.getRouteStop().getId(), RouteStopAssignment::getSequence));

                assertEquals(1, finalSequences.get(routeStop2.getId()));
                assertEquals(2, finalSequences.get(routeStop1.getId()));
            }
        }

        @Nested
        class failureScenarios {

            @BeforeEach
            void setUp() {
                // associar pontos de parada
                RouteStopAssignment routeStopAssignment = new RouteStopAssignment();
                routeStopAssignment.setRouteStop(routeStop);
                routeStopAssignment.setSequence(3);

                standardRoute.setRouteStopAssignments(List.of(routeStopAssignment));
            }

            @Test
            @DisplayName("Deve lançar exception quando o StandardRouteId for NULL")
            void shouldThrowIllegalArgumentExceptionWhenStandardRouteIdIsNull() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                        standardRouteService.reorderRouteStops(null, List.of())
                );

                assertEquals("standardRouteId não pode ser nulo", exception.getMessage());

                verifyNoInteractions(standardRouteRepository);
                verifyNoInteractions(routeStopRepository);
                verifyNoInteractions(mapboxAPIService);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o DTO routeStopsReorder for null ou vazio")
            @NullAndEmptySource // gera dois testes: com empty String e null value para o parâmetro
            void shouldThrowDomainValidationExceptionWhenRouteStopsReorderIsNullOrEmpty(List<RouteStopReorderRequestDTO> routeStopsReorder) {
                DomainValidationException exception = assertThrows(DomainValidationException.class, () ->
                        standardRouteService.reorderRouteStops(UUID.randomUUID(), routeStopsReorder)
                );

                assertEquals("É necessário informar os pontos de parada para reordenar", exception.getMessage());

                verifyNoInteractions(standardRouteRepository);
                verifyNoInteractions(routeStopRepository);
                verifyNoInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando não encontrar a Rota Padrão")
            void shouldThrowEntityNotFoundExceptionWhenStandardRouteNotFound() {
                UUID standardRouteId = UUID.randomUUID();
                List<RouteStopReorderRequestDTO> routeStopsReorder = List.of(
                        new RouteStopReorderRequestDTO(UUID.randomUUID(), 1)
                );

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.empty());

                EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                        standardRouteService.reorderRouteStops(standardRouteId, routeStopsReorder)
                );

                assertTrue(exception.getMessage().contains("Rota padrão não encontrada:"));

                verify(standardRouteRepository).findById(standardRouteId);
                verifyNoInteractions(routeStopRepository);
                verifyNoInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando a Rota Padrão for inativa")
            void shouldThrowDomainValidationExceptionWhenStandardRouteIsInactive() {
                UUID standardRouteId = standardRoute.getId();
                standardRoute.setStatus(GeneralStatus.INACTIVE);

                List<RouteStopReorderRequestDTO> routeStopsReorder = List.of(
                        new RouteStopReorderRequestDTO(UUID.randomUUID(), 1)
                );

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));

                DomainValidationException exception = assertThrows(DomainValidationException.class, () ->
                        standardRouteService.reorderRouteStops(standardRouteId, routeStopsReorder)
                );

                assertEquals("Não é possível reorganizar os pontos de uma rota padrão inativa", exception.getMessage());

                verify(standardRouteRepository).findById(standardRouteId);
                verifyNoInteractions(routeStopRepository);
                verifyNoInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando o RouteStopId for NULL")
            void shouldThrowDomainValidationExceptionWhenRouteStopIdIsNull() {
                RouteStopAssignment routeStopAssignment = new RouteStopAssignment();
                routeStopAssignment.setRouteStop(routeStop);
                routeStopAssignment.setSequence(3);

                standardRoute.setRouteStopAssignments(List.of(routeStopAssignment));

                List<RouteStopReorderRequestDTO> routeStopsReorder = List.of(
                        new RouteStopReorderRequestDTO(null, 1)
                );

                when(standardRouteRepository.findById(standardRoute.getId())).thenReturn(Optional.of(standardRoute));

                DomainValidationException exception = assertThrows(DomainValidationException.class, () ->
                        standardRouteService.reorderRouteStops(standardRoute.getId(), routeStopsReorder)
                );

                assertEquals("RouteStopId não pode ser nulo", exception.getMessage());

                verify(standardRouteRepository).findById(standardRoute.getId());
                verifyNoInteractions(routeStopRepository);
                verifyNoInteractions(mapboxAPIService);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando a nova sequência for inválida (zero ou negativa)")
            @ValueSource(ints = {0, -1})
            void shouldThrowDomainValidationExceptionWhenNewSequenceIsZeroOrNegative(int newSequence) {
                UUID standardRouteId = standardRoute.getId();

                List<RouteStopReorderRequestDTO> routeStopsReorder = List.of(
                        new RouteStopReorderRequestDTO(UUID.randomUUID(), newSequence)
                );

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));

                DomainValidationException exception = assertThrows(DomainValidationException.class, () ->
                        standardRouteService.reorderRouteStops(standardRouteId, routeStopsReorder)
                );

                assertEquals("A nova sequência deve ser maior que zero", exception.getMessage());

                verify(standardRouteRepository).findById(standardRouteId);
                verifyNoInteractions(routeStopRepository);
                verifyNoInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando o RouteStopId for duplicado (tentativa de inserir o mesmo routeStop)")
            void shouldThrowDomainValidationExceptionWhenRouteStopIdIsDuplicated() {
                standardRoute.setRouteStopAssignments(new ArrayList<>());

                RouteStop newRouteStop = new RouteStop();
                newRouteStop.setId(UUID.randomUUID());
                newRouteStop.setLongitude(-38.232);
                newRouteStop.setLatitude(-11.233);
                newRouteStop.setCustomer(customer);

                RouteStopAssignment newRouteStopAssignment = new RouteStopAssignment();
                newRouteStopAssignment.setSequence(2);
                newRouteStopAssignment.setRouteStop(newRouteStop);

                RouteStopAssignment routeStopAssignment = new RouteStopAssignment();
                routeStopAssignment.setRouteStop(routeStop);
                routeStopAssignment.setSequence(3);

                standardRoute.setRouteStopAssignments(List.of(newRouteStopAssignment, routeStopAssignment));

                UUID standardRouteId = standardRoute.getId();
                UUID duplicatedRouteStopId = UUID.randomUUID();

                List<RouteStopReorderRequestDTO> routeStopsReorder = List.of(
                        new RouteStopReorderRequestDTO(duplicatedRouteStopId, 1),
                        new RouteStopReorderRequestDTO(duplicatedRouteStopId, 2)
                );

                System.out.println("ids: " + routeStopsReorder.stream().map(RouteStopReorderRequestDTO::routeStopId).toList());


                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));

                DomainValidationException exception = assertThrows(DomainValidationException.class, () ->
                        standardRouteService.reorderRouteStops(standardRouteId, routeStopsReorder)
                );

                System.out.println("exception.getMessage(): " + exception.getMessage());

//                assertTrue(exception.getMessage().contains("RouteStopId duplicado:"));
                assertTrue(exception.getMessage().contains(duplicatedRouteStopId.toString()));

                verify(standardRouteRepository).findById(standardRouteId);
                verifyNoInteractions(routeStopRepository);
                verifyNoInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando a nova sequência for duplicada (já existir p/ outra parada)")
            void shouldThrowDomainValidationExceptionWhenNewSequenceIsDuplicated() {
                UUID standardRouteId = standardRoute.getId();

                standardRoute.setRouteStopAssignments(new ArrayList<>());

                RouteStop newRouteStop = new RouteStop();
                newRouteStop.setId(UUID.randomUUID());
                newRouteStop.setLongitude(-38.232);
                newRouteStop.setLatitude(-11.233);
                newRouteStop.setCustomer(customer);

                RouteStopAssignment newRouteStopAssignment = new RouteStopAssignment();
                newRouteStopAssignment.setSequence(2);
                newRouteStopAssignment.setRouteStop(newRouteStop);

                RouteStopAssignment routeStopAssignment = new RouteStopAssignment();
                routeStopAssignment.setRouteStop(routeStop);
                routeStopAssignment.setSequence(3);

                standardRoute.setRouteStopAssignments(List.of(newRouteStopAssignment, routeStopAssignment));

                List<RouteStopReorderRequestDTO> routeStopsReorder = List.of(
                        new RouteStopReorderRequestDTO(UUID.randomUUID(), 1),
                        new RouteStopReorderRequestDTO(UUID.randomUUID(), 1)
                );

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));

                DomainValidationException exception = assertThrows(DomainValidationException.class, () ->
                        standardRouteService.reorderRouteStops(standardRouteId, routeStopsReorder)
                );

                assertTrue(exception.getMessage().contains("newSequence duplicada:"));

                verify(standardRouteRepository).findById(standardRouteId);
                verifyNoInteractions(routeStopRepository);
                verifyNoInteractions(mapboxAPIService);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando as sequências não forem consecultivas começando em 1. (deve ser: 1, 2, 3...)")
            @MethodSource("provideNonConsecutiveSequences")
            void shouldThrowDomainValidationExceptionWhenSequencesAreNotConsecutiveStartingAtOne(List<Integer> sequences) {
                UUID standardRouteId = standardRoute.getId();

                standardRoute.setRouteStopAssignments(new ArrayList<>());

                RouteStop newRouteStop = new RouteStop();
                newRouteStop.setId(UUID.randomUUID());
                newRouteStop.setLongitude(-38.232);
                newRouteStop.setLatitude(-11.233);
                newRouteStop.setCustomer(customer);

                RouteStopAssignment newRouteStopAssignment = new RouteStopAssignment();
                newRouteStopAssignment.setSequence(2);
                newRouteStopAssignment.setRouteStop(newRouteStop);

                RouteStopAssignment routeStopAssignment = new RouteStopAssignment();
                routeStopAssignment.setRouteStop(routeStop);
                routeStopAssignment.setSequence(3);

                standardRoute.setRouteStopAssignments(List.of(newRouteStopAssignment, routeStopAssignment));

                List<RouteStopReorderRequestDTO> routeStopsReorder = sequences.stream()
                        .map(seq -> new RouteStopReorderRequestDTO(UUID.randomUUID(), seq))
                        .toList();

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));

                DomainValidationException exception = assertThrows(DomainValidationException.class, () ->
                        standardRouteService.reorderRouteStops(standardRouteId, routeStopsReorder)
                );

                System.out.println("routeStopsReorder: " + routeStopsReorder.size());
                System.out.println("standardRoute.getRouteStopAssignments(): " + standardRoute.getRouteStopAssignments().size());

                assertEquals("As novas sequências devem ser consecutivas começando em 1", exception.getMessage());

                verify(standardRouteRepository).findById(standardRouteId);
                verifyNoInteractions(routeStopRepository);
                verifyNoInteractions(mapboxAPIService);
            }

            public static Stream<Arguments> provideNonConsecutiveSequences() {
                return Stream.of(
                        Arguments.of(List.of(1, 3)),
//                        Arguments.of(List.of(2, 3)),
                        Arguments.of(List.of(1, 4))
//                        Arguments.of(List.of(3, 4, 5))
                );
            }

            @Test
            @DisplayName("Deve lançar exception quando o Ponto de Parada não estiver associado a uma Rota Padrão")
            void shouldThrowEntityNotFoundExceptionWhenRouteStopIsNotAssociatedWithRoute() {
                UUID standardRouteId = standardRoute.getId();
                UUID associatedRouteStopId = UUID.randomUUID();
                UUID notAssociatedRouteStopId = UUID.randomUUID();

                RouteStop associatedRouteStop = new RouteStop(associatedRouteStopId, "Stop", "Desc", -12.0, -38.0, customer, GeneralStatus.ACTIVE);
                RouteStopAssignment assignment = new RouteStopAssignment();
                assignment.setRouteStop(associatedRouteStop);
                assignment.setSequence(1);

                standardRoute.setRouteStopAssignments(new ArrayList<>(List.of(assignment)));

                List<RouteStopReorderRequestDTO> routeStopsReorder = List.of(
                        new RouteStopReorderRequestDTO(notAssociatedRouteStopId, 1)
                );

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));

                EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                        standardRouteService.reorderRouteStops(standardRouteId, routeStopsReorder)
                );

                assertTrue(exception.getMessage().contains("não está associado à rota"));

                verify(standardRouteRepository).findById(standardRouteId);
                verifyNoInteractions(routeStopRepository);
                verifyNoInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando algum Ponto de Parada não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenSomeRouteStopsNotFound() {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopId1 = UUID.randomUUID();
                UUID routeStopId2 = UUID.randomUUID();

                RouteStop routeStop1 = new RouteStop(routeStopId1, "Stop 1", "Desc", -12.0, -38.0, customer, GeneralStatus.ACTIVE);
                RouteStop routeStop2 = new RouteStop(routeStopId2, "Stop 2", "Desc", -12.1, -38.1, customer, GeneralStatus.ACTIVE);

                RouteStopAssignment assignment1 = new RouteStopAssignment();
                assignment1.setRouteStop(routeStop1);
                assignment1.setSequence(1);

                RouteStopAssignment assignment2 = new RouteStopAssignment();
                assignment2.setRouteStop(routeStop2);
                assignment2.setSequence(2);

                standardRoute.setRouteStopAssignments(new ArrayList<>(List.of(assignment1, assignment2)));

                List<RouteStopReorderRequestDTO> routeStopsReorder = List.of(
                        new RouteStopReorderRequestDTO(routeStopId1, 1),
                        new RouteStopReorderRequestDTO(routeStopId2, 2)
                );

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                // Retorna apenas um dos dois RouteStops solicitados
                when(routeStopRepository.findAllById(anyCollection())).thenReturn(List.of(routeStop1));

                EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                        standardRouteService.reorderRouteStops(standardRouteId, routeStopsReorder)
                );

                assertEquals("Um ou mais RouteStops informados não foram encontrados", exception.getMessage());

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findAllById(anyCollection());
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }

            @Test
            @DisplayName("Deve lançar exception quando os Customers forem diferentes")
            void shouldThrowExceptionWhenRouteStopBelongsToDifferentCustomer() {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopId = UUID.randomUUID();

                Customer differentCustomer = new Customer();
                differentCustomer.setId(UUID.randomUUID());

                RouteStop routeStop = new RouteStop(routeStopId, "Stop", "Desc", -12.0, -38.0, differentCustomer, GeneralStatus.ACTIVE);

                RouteStopAssignment assignment = new RouteStopAssignment();
                assignment.setRouteStop(routeStop);
                assignment.setSequence(1);

                standardRoute.setRouteStopAssignments(new ArrayList<>(List.of(assignment)));

                List<RouteStopReorderRequestDTO> routeStopsReorder = List.of(
                        new RouteStopReorderRequestDTO(routeStopId, 1)
                );

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findAllById(anyCollection())).thenReturn(List.of(routeStop));

                assertThrows(RuntimeException.class, () ->
                        standardRouteService.reorderRouteStops(standardRouteId, routeStopsReorder)
                );

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findAllById(anyCollection());
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }

            @Test
            @DisplayName("Deve lançar exception quando o Ponto de Parada estiver inativo")
            void shouldThrowDomainValidationExceptionWhenRouteStopIsInactive() {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopId = UUID.randomUUID();

                RouteStop routeStop = new RouteStop(routeStopId, "Stop", "Desc", -12.0, -38.0, customer, GeneralStatus.INACTIVE);

                RouteStopAssignment assignment = new RouteStopAssignment();
                assignment.setRouteStop(routeStop);
                assignment.setSequence(1);

                standardRoute.setRouteStopAssignments(new ArrayList<>(List.of(assignment)));

                List<RouteStopReorderRequestDTO> routeStopsReorder = List.of(
                        new RouteStopReorderRequestDTO(routeStopId, 1)
                );

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findAllById(anyCollection())).thenReturn(List.of(routeStop));

                DomainValidationException exception = assertThrows(DomainValidationException.class, () ->
                        standardRouteService.reorderRouteStops(standardRouteId, routeStopsReorder)
                );

                assertTrue(exception.getMessage().contains("está inativo e não pode participar da rota"));

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findAllById(anyCollection());
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o Ponto de Parada estiver sem ou com coodenadas inválidas")
            @MethodSource("provideRouteStopsWithNullCoordinates")
            void shouldThrowDomainValidationExceptionWhenRouteStopHasNullCoordinates(Double latitude, Double longitude) {
                UUID standardRouteId = standardRoute.getId();
                UUID routeStopId = UUID.randomUUID();

                RouteStop routeStop = new RouteStop(routeStopId, "Stop", "Desc", longitude, latitude, customer, GeneralStatus.ACTIVE);

                RouteStopAssignment assignment = new RouteStopAssignment();
                assignment.setRouteStop(routeStop);
                assignment.setSequence(1);

                standardRoute.setRouteStopAssignments(new ArrayList<>(List.of(assignment)));

                List<RouteStopReorderRequestDTO> routeStopsReorder = List.of(
                        new RouteStopReorderRequestDTO(routeStopId, 1)
                );

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));
                when(routeStopRepository.findAllById(anyCollection())).thenReturn(List.of(routeStop));

                DomainValidationException exception = assertThrows(DomainValidationException.class, () ->
                        standardRouteService.reorderRouteStops(standardRouteId, routeStopsReorder)
                );

                assertTrue(exception.getMessage().contains("não possui coordenadas válidas"));

                verify(standardRouteRepository).findById(standardRouteId);
                verify(routeStopRepository).findAllById(anyCollection());
                verifyNoInteractions(mapboxAPIService);
                verify(standardRouteRepository, never()).save(any());
            }

            public static Stream<Arguments> provideRouteStopsWithNullCoordinates() {
                return Stream.of(
                        Arguments.of(null, -38.0),
                        Arguments.of(-12.0, null)
                );
            }
        }
    }
}