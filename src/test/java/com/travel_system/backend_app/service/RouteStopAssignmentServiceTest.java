package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.CustomerMismatchException;
import com.travel_system.backend_app.exceptions.DomainValidationException;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopAssignmentRequestDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopReorderRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteRequestDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.repository.RouteStopRepository;
import com.travel_system.backend_app.repository.StandardRouteRepository;
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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteStopAssignmentServiceTest {
    
    private RouteStopAssignmentService routeStopAssignmentService;

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

        routeStopAssignmentService = new RouteStopAssignmentService(
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

                routeStopAssignmentService.associateRouteStopWithStandardRoute(standardRoute.getId(), routeStop.getId(), sequence, isOptionalSpot);

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
                        routeStopAssignmentService.associateRouteStopWithStandardRoute(standardRoute.getId(), routeStop.getId(), sequence, false)
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
                        routeStopAssignmentService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, 1, false)
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
                        routeStopAssignmentService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, 1, false)
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
                        routeStopAssignmentService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, 1, false)
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
                        routeStopAssignmentService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, 1, false)
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
                        routeStopAssignmentService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, 2, false)
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
                        routeStopAssignmentService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, sequenceToTest, false)
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

                routeStopAssignmentService.removeRouteStopWithStandardRoute(standardRouteId, routeStopIdToRemove);

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

                routeStopAssignmentService.removeRouteStopWithStandardRoute(standardRouteId, routeStopIdToRemove);

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
                        routeStopAssignmentService.removeRouteStopWithStandardRoute(standardRouteId, routeStopId)
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
                        routeStopAssignmentService.removeRouteStopWithStandardRoute(standardRouteId, routeStopId)
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
                        routeStopAssignmentService.removeRouteStopWithStandardRoute(standardRouteId, routeStopId)
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
                        routeStopAssignmentService.removeRouteStopWithStandardRoute(standardRouteId, routeStopId)
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

                RouteStop routeStop1 = new RouteStop(UUID.randomUUID(), "Stop 1", "Desc", -12.0, -38.0, customer, GeneralStatus.ACTIVE, Instant.now(), null);
                RouteStop routeStop2 = new RouteStop(UUID.randomUUID(), "Stop 2", "Desc", -12.1, -38.1, customer, GeneralStatus.ACTIVE, Instant.now(), null);

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

                StandardRouteResponseDTO result = routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder);

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
                        routeStopAssignmentService.reorderRouteStops(null, List.of())
                );

                assertEquals("standardRouteId não pode ser nulo", exception.getMessage());

                verifyNoInteractions(standardRouteRepository);
                verifyNoInteractions(routeStopRepository);
                verifyNoInteractions(mapboxAPIService);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o DTO routeStopsReorder for null ou vazio")
            @NullAndEmptySource
                // gera dois testes: com empty String e null value para o parâmetro
            void shouldThrowDomainValidationExceptionWhenRouteStopsReorderIsNullOrEmpty(List<RouteStopReorderRequestDTO> routeStopsReorder) {
                DomainValidationException exception = assertThrows(DomainValidationException.class, () ->
                        routeStopAssignmentService.reorderRouteStops(UUID.randomUUID(), routeStopsReorder)
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
                        routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder)
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
                        routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder)
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
                        routeStopAssignmentService.reorderRouteStops(standardRoute.getId(), routeStopsReorder)
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
                        routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder)
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
                        routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder)
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
                        routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder)
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
                        routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder)
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

                RouteStop associatedRouteStop = new RouteStop(associatedRouteStopId, "Stop", "Desc", -12.0, -38.0, customer, GeneralStatus.ACTIVE,Instant.now(), null);
                RouteStopAssignment assignment = new RouteStopAssignment();
                assignment.setRouteStop(associatedRouteStop);
                assignment.setSequence(1);

                standardRoute.setRouteStopAssignments(new ArrayList<>(List.of(assignment)));

                List<RouteStopReorderRequestDTO> routeStopsReorder = List.of(
                        new RouteStopReorderRequestDTO(notAssociatedRouteStopId, 1)
                );

                when(standardRouteRepository.findById(standardRouteId)).thenReturn(Optional.of(standardRoute));

                EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                        routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder)
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

                RouteStop routeStop1 = new RouteStop(routeStopId1, "Stop 1", "Desc", -12.0, -38.0, customer, GeneralStatus.ACTIVE, Instant.now(), null);
                RouteStop routeStop2 = new RouteStop(routeStopId2, "Stop 2", "Desc", -12.1, -38.1, customer, GeneralStatus.ACTIVE, Instant.now(), null);

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
                        routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder)
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

                RouteStop routeStop = new RouteStop(routeStopId, "Stop", "Desc", -12.0, -38.0, differentCustomer, GeneralStatus.ACTIVE,Instant.now(), null);

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
                        routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder)
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

                RouteStop routeStop = new RouteStop(routeStopId, "Stop", "Desc", -12.0, -38.0, customer, GeneralStatus.INACTIVE,Instant.now(), null);

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
                        routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder)
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

                RouteStop routeStop = new RouteStop(routeStopId, "Stop", "Desc", longitude, latitude, customer, GeneralStatus.ACTIVE, Instant.now(), null);

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
                        routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder)
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