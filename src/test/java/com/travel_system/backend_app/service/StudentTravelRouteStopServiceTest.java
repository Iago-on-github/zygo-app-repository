package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.routestops_algorithm.*;
import com.travel_system.backend_app.listeners.routestops_algorithm.*;
import com.travel_system.backend_app.model.dtos.cache.StudentTravelRouteStopTrackingCacheDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.repository.StudentTravelRouteStopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static com.travel_system.backend_app.config.constants.GlobalAppConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentTravelRouteStopServiceTest {

    @InjectMocks
    private StudentTravelRouteStopService studentTravelRouteStopService;

    @Mock
    private RouteCalculationService routeCalculationService;
    @Mock
    private RedisTrackingService redisTrackingService;
    @Mock
    private TravelTrackingStaticCacheService travelTrackingStaticCacheService;
    @Mock
    private TravelTrackingNotificationService travelTrackingNotificationService;

    @Mock
    private StudentTravelRouteStopRepository studentTravelRouteStopRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private InvalidTravelStudentRouteStopListener invalidTravelStudentRouteStopListener;
    @InjectMocks
    private InitializeStudentTravelRouteStopListener initializeStudentTravelRouteStopListener;
    @InjectMocks
    private ProcessStudentTravelRouteStopApproachingListener processStudentTravelRouteStopApproachingListener;
    @InjectMocks
    private ConfirmStudentTravelRouteStopListener confirmStudentTravelRouteStopListener;
    @InjectMocks
    private CancelledStudentTravelRouteStopListener cancelledStudentTravelRouteStopListener;

    @Nested
    class validateStudentTravelRouteStop {

        @Nested
        class successScenarios {
            InvalidStudentTravelRouteStopEvent eventDTO;

            @BeforeEach
            void setUp() {
                eventDTO = new InvalidStudentTravelRouteStopEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), StudentTravelRouteStopStatus.INVALID_ROUTE, Instant.now());
            }

            @Test
            @DisplayName("Deve publicar o evento de Rota Inválida com todos os dados corretos")
            void shouldPublishInvalidRouteStopEventWithCorrectData() {
                studentTravelRouteStopService.validateStudentTravelRouteStop(eventDTO.travelId(), eventDTO.studentTravelId(), eventDTO.studentId(), eventDTO.customerId());

                ArgumentCaptor<InvalidStudentTravelRouteStopEvent> invalidEventDtoCaptor = ArgumentCaptor.forClass(InvalidStudentTravelRouteStopEvent.class);

                verify(eventPublisher, times(1)).publishEvent(any(InvalidStudentTravelRouteStopEvent.class));
                verify(eventPublisher, times(1)).publishEvent(invalidEventDtoCaptor.capture());

                InvalidStudentTravelRouteStopEvent capturedEvent = invalidEventDtoCaptor.getValue();

                assertEquals(capturedEvent.studentId(), eventDTO.studentId());
                assertEquals(capturedEvent.studentTravelId(), eventDTO.studentTravelId());
                assertEquals(capturedEvent.travelId(), eventDTO.travelId());
                assertEquals(capturedEvent.studentTravelRouteStopStatus(), eventDTO.studentTravelRouteStopStatus());

                assertNotNull(capturedEvent.lastValidatedAt());
            }

            @Test
            @DisplayName("Deve chamar o serviço de notificação ao receber o evento do estudante não associado")
            void shouldTriggerNotificationServiceWhenInvalidRouteStopEventIsReached() {
                invalidTravelStudentRouteStopListener.handleInvalidStudentTravelRouteStop(eventDTO);

                verify(travelTrackingNotificationService, times(1)).sendNotAssociatedToRouteStopNotification(any(InvalidStudentTravelRouteStopEvent.class));

                verifyNoMoreInteractions(travelTrackingNotificationService);
            }
        }

        @Nested
        class failureScenarios {
            InvalidStudentTravelRouteStopEvent eventDTO;

            @BeforeEach
            void setUp() {
                eventDTO = new InvalidStudentTravelRouteStopEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), StudentTravelRouteStopStatus.INVALID_ROUTE, Instant.now());
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando um dos parâmetros requeridos forem inválidos ou null")
            @MethodSource("nullRequireParameterProvider")
            void shouldThrowIllegalArgumentExceptionWhenMandatoryParametersAreNull(UUID travelId, UUID studentTravelId, UUID studentId, UUID customerId) {
                assertThrows(IllegalArgumentException.class, () -> studentTravelRouteStopService.validateStudentTravelRouteStop(travelId, studentTravelId, studentId, customerId));

                verifyNoInteractions(eventPublisher);

                verify(travelTrackingNotificationService, never()).sendNotAssociatedToRouteStopNotification(any());

            }

            public static Stream<Arguments> nullRequireParameterProvider() {
                return Stream.of(
                        Arguments.of(null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                        Arguments.of(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID()),
                        Arguments.of(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID()),
                        Arguments.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null)
                );
            }

            @Test
            @DisplayName("Deve lidar corretamente com o recebimento de um evento invalido (null), ignorando-o sem executar as ações do Listener")
            void shouldNotProcessAnythingWhenEventIsNull() {
                eventDTO = null; // null explicíto

                invalidTravelStudentRouteStopListener.handleInvalidStudentTravelRouteStop(eventDTO);

                verify(travelTrackingNotificationService, never()).sendNotAssociatedToRouteStopNotification(any());

                verifyNoInteractions(travelTrackingNotificationService);
            }
        }
    }

    @Nested
    class initializeStudentTravelRouteStopTracking {
        UUID travelId;
        UUID studentTravelId;

        InitializeStudentTravelRouteStopEvent eventDTO;
        StudentTravelRouteStopTrackingCacheDTO cacheDTO;

        @BeforeEach
        void setUp() {
            travelId = UUID.randomUUID();
            studentTravelId = UUID.randomUUID();

            cacheDTO = new StudentTravelRouteStopTrackingCacheDTO(studentTravelId, UUID.randomUUID(), travelId, UUID.randomUUID(), -11.372, -32.111, TravelPeriod.EVENING, StudentTravelRouteStopStatus.EXPECTED, MONITORING_THRESHOLD);
            eventDTO = new InitializeStudentTravelRouteStopEvent(cacheDTO.studentTravelId(), cacheDTO.travelId(), cacheDTO.routeStopId(), cacheDTO.routeStopLatitude(), cacheDTO.routeStopLongitude(), cacheDTO.status());

        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realizar a publicação do evento de inicialização com dados providos do cache")
            void shouldPublishInitializeEventWithCacheData() {
                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(cacheDTO);

                studentTravelRouteStopService.initializeStudentTravelRouteStopTracking(travelId, studentTravelId);

                ArgumentCaptor<InitializeStudentTravelRouteStopEvent> invalidEventArgCaptor = ArgumentCaptor.forClass(InitializeStudentTravelRouteStopEvent.class);

                verify(eventPublisher, times(1)).publishEvent(invalidEventArgCaptor.capture());

                InitializeStudentTravelRouteStopEvent eventValue = invalidEventArgCaptor.getValue();

                assertEquals(eventDTO.studentTravelId(), eventValue.studentTravelId());
                assertEquals(eventDTO.travelId(), eventValue.travelId());
                assertEquals(eventDTO.routeStopId(), eventValue.routeStopId());
                assertEquals(eventDTO.routeStopLatitude(), eventValue.routeStopLatitude());
                assertEquals(eventDTO.routeStopLongitude(), eventValue.routeStopLongitude());
                assertEquals(eventDTO.studentTravelRouteStopStatus(), eventValue.studentTravelRouteStopStatus());

                verify(travelTrackingStaticCacheService, times(1)).getStudentTravelTrackingData(eq(travelId), eq(studentTravelId));
            }

            @Test
            @DisplayName("Deve realizar o armazenamento dos dados de incialziação no cache quando o evento for recebido")
            void shouldStoreInitializationDataInRedisWhenEventIsReceived() {
                initializeStudentTravelRouteStopListener.handleInitializeStudentTravelRouteStop(eventDTO);

                // verifica os dados passados
                verify(redisTrackingService, times(1))
                        .storeInitializeStudentTravelRouteStopData(argThat(cacheData -> cacheData.studentTravelId().equals(eventDTO.studentTravelId()) &&
                                cacheData.travelId().equals(eventDTO.travelId()) &&
                                cacheData.routeStopId().equals(eventDTO.routeStopId()) &&
                                cacheData.routeStopLatitude().equals(eventDTO.routeStopLatitude()) &&
                                cacheData.routeStopLongitude().equals(eventDTO.routeStopLongitude()) &&
                                cacheData.studentTravelRouteStopStatus().equals(eventDTO.studentTravelRouteStopStatus())));
            }
        }

        @Nested
        class failureScenarios {

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando os parâmetros requeridos não forem enviados")
            @MethodSource("nullRequireParameterProvider")
            void shouldThrowIllegalArgumentExceptionWhenRequireParametersNotFound(UUID invalidTravelId, UUID invalidStudentTravelId) {
                assertThrows(IllegalArgumentException.class, () -> studentTravelRouteStopService.initializeStudentTravelRouteStopTracking(invalidTravelId, invalidStudentTravelId));

                verifyNoInteractions(eventPublisher ,redisTrackingService);
            }

            public static Stream<Arguments> nullRequireParameterProvider() {
                return Stream.of(
                        Arguments.of(null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                        Arguments.of(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID())
                );
            }

            @Test
            @DisplayName("Deve retornar silenciosamente quando os dados providos do cache forem null, não deve publicar evento")
            void shouldReturnEarlyWhenDataProvidedByCacheIsNull() {
                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(null);

                studentTravelRouteStopService.initializeStudentTravelRouteStopTracking(travelId, studentTravelId);

                verifyNoInteractions(eventPublisher, redisTrackingService);
                
                verify(travelTrackingStaticCacheService, times(1)).getStudentTravelTrackingData(eq(travelId), eq(studentTravelId));
            }

            @Test
            @DisplayName("Não deve realizar nenhum processamento dentro do Listener quando o evento recebido for null")
            void shouldNotProcessAnythingWhenEventIsNull() {
                initializeStudentTravelRouteStopListener.handleInitializeStudentTravelRouteStop(null);

                verifyNoInteractions(redisTrackingService);
            }
        }
    }

    @Nested
    class processRouteStopApproach {
        UUID travelId;
        UUID studentTravelId;

        ProcessStudentTravelRouteStopApproachingEvent eventDTO;
        StudentTravelRouteStopTrackingCacheDTO cacheDTO;
        LiveLocationDTO liveLocationDTO;

        @BeforeEach
        void setUp() {
            travelId = UUID.randomUUID();
            studentTravelId = UUID.randomUUID();

            cacheDTO = new StudentTravelRouteStopTrackingCacheDTO(studentTravelId, UUID.randomUUID(), travelId, UUID.randomUUID(), -11.372, -32.111, TravelPeriod.EVENING, StudentTravelRouteStopStatus.EXPECTED, MONITORING_THRESHOLD);
            eventDTO = new ProcessStudentTravelRouteStopApproachingEvent(studentTravelId, cacheDTO.studentId(), travelId, cacheDTO.routeStopId(), APPROACHING_THRESHOLD, Instant.now());
            liveLocationDTO = new LiveLocationDTO(-12.9714, -38.5014, "_p~iF~ps|U_ulLnnqC_mqNvxq`@", 14250.50, -12.9700, -38.5000, Instant.parse("2026-08-24T23:49:33Z"));
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve publicar evento de aproximação quando todos os críterios (distance, status) forem processados ")
            void shouldPublishApproachingEventWhenDistanceIsWithinThresholdAndStatusIsExpected() {
                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(cacheDTO);
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        cacheDTO.routeStopLatitude(),
                        cacheDTO.routeStopLongitude(),
                        liveLocationDTO.latitude(),
                        liveLocationDTO.longitude())).thenReturn(1.5); // igual que o threshould de 1.5

                studentTravelRouteStopService.processRouteStopApproach(travelId, studentTravelId);

                ArgumentCaptor<ProcessStudentTravelRouteStopApproachingEvent> processEventArgCaptor = ArgumentCaptor.forClass(ProcessStudentTravelRouteStopApproachingEvent.class);

                verify(eventPublisher, times(1)).publishEvent(processEventArgCaptor.capture());

                ProcessStudentTravelRouteStopApproachingEvent eventValue = processEventArgCaptor.getValue();

                assertEquals(eventValue.studentTravelId(), eventDTO.studentTravelId());
                assertEquals(eventValue.studentId(), eventDTO.studentId());
                assertEquals(eventValue.travelId(), eventDTO.travelId());
                assertEquals(eventValue.routeStopId(), eventDTO.routeStopId());
                assertEquals(eventValue.distance(), eventDTO.distance());

                assertNotNull(eventValue.occurredAt());

                verify(travelTrackingStaticCacheService, times(2)).getStudentTravelTrackingData(eq(travelId), eq(studentTravelId));
                verify(redisTrackingService, times(1)).getLiveLocation(eq(travelId));
                verify(routeCalculationService, times(1)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());
            }

            @Test
            @DisplayName("Deve realizar a atualização do monitoriamento no redis ao receber umnovo ping de aproximação válido")
            void shouldUpdateMonitoringInRedisWhenApproachingEventIsReceived() {
                processStudentTravelRouteStopApproachingListener.handleProcessRouteStopApproaching(eventDTO);

                verify(redisTrackingService, times(1))
                        .updateStudentTravelRouteStopProcessMonitoring(argThat(cacheData -> cacheData.studentTravelId().equals(eventDTO.studentTravelId()) &&
                        cacheData.studentId().equals(eventDTO.studentId()) &&
                        cacheData.travelId().equals(eventDTO.travelId()) &&
                        cacheData.routeStopId().equals(eventDTO.routeStopId()) &&
                        cacheData.distance().equals(eventDTO.distance()) &&
                        cacheData.occurredAt() != null));

                verifyNoMoreInteractions(redisTrackingService);
            }
        }

        @Nested
        class failureScenarios {

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando os parâmetros requeridos não forem enviados")
            @MethodSource("nullRequireParameterProvider")
            void shouldThrowIllegalArgumentExceptionWhenRequireParametersNotFound(UUID invalidTravelId, UUID invalidStudentTravelId) {
                assertThrows(IllegalArgumentException.class, () -> studentTravelRouteStopService.processRouteStopApproach(invalidTravelId, invalidStudentTravelId));

                verifyNoInteractions(eventPublisher, redisTrackingService, travelTrackingStaticCacheService, routeCalculationService);
            }

            public static Stream<Arguments> nullRequireParameterProvider() {
                return Stream.of(
                        Arguments.of(null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                        Arguments.of(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID())
                );
            }

            @Test
            @DisplayName("Deve retornar silenciosamente quando os dados providos do cache forem null, não deve publicar evento")
            void shouldReturnEarlyWhenDataProvidedByCacheIsNull() {
                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(null);

                studentTravelRouteStopService.processRouteStopApproach(travelId, studentTravelId);

                verifyNoInteractions(eventPublisher, redisTrackingService, routeCalculationService);

                verify(travelTrackingStaticCacheService, times(1)).getStudentTravelTrackingData(eq(travelId), eq(studentTravelId));
            }

            @ParameterizedTest
            @DisplayName("Deve retornar de forma silenciosa quando a distance calculada for inválida")
            @MethodSource("invalidCalculatedDistanceProvider")
            void shouldReturnEarlyWhenCalculatedDistanceIsInvalid(double calculatedDistance) {
                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(cacheDTO);
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        cacheDTO.routeStopLatitude(),
                        cacheDTO.routeStopLongitude(),
                        liveLocationDTO.latitude(),
                        liveLocationDTO.longitude())).thenReturn(calculatedDistance);

                studentTravelRouteStopService.processRouteStopApproach(travelId, studentTravelId);

                verifyNoMoreInteractions(travelTrackingStaticCacheService, redisTrackingService, routeCalculationService);

                verifyNoInteractions(eventPublisher);
            }

            public static Stream<Arguments> invalidCalculatedDistanceProvider() {
                return Stream.of(
                        Arguments.of(-1),
                        Arguments.of(-10)
                );
            }

            @ParameterizedTest
            @DisplayName("Deve retornar de forma silenciosa quando a position do Driver provida pelo Cache for invalida")
            @MethodSource("invalidDriverPositionProvider")
            void shouldReturnEarlyWhenDriverPositionProvidedByRedisIsNull(Double driverLat, Double driverLong) {
                LiveLocationDTO invalidLiveLoc = new LiveLocationDTO(driverLat, driverLong, "_p~iF~ps|U_ulLnnqC_mqNvxq`@", 14250.50, -12.9700, -38.5000, Instant.parse("2026-08-24T23:49:33Z"));

                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(cacheDTO);
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(invalidLiveLoc);

                studentTravelRouteStopService.processRouteStopApproach(travelId, studentTravelId);

                verifyNoMoreInteractions(travelTrackingStaticCacheService, redisTrackingService, routeCalculationService);

                verifyNoInteractions(eventPublisher, routeCalculationService);
            }

            public static Stream<Arguments> invalidDriverPositionProvider() {
                return Stream.of(
                        Arguments.of(null, -11.2323),
                        Arguments.of(-32.4532, null)
                );
            }

            @Test
            @DisplayName("Deve ignorar o processamento se a distância ao Ponto de Parada for maior que o limite de aproximação")
            void shouldNotPublishingEventWhenDistanceExceedsApproachingThreshold() {
                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(cacheDTO);
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        cacheDTO.routeStopLatitude(),
                        cacheDTO.routeStopLongitude(),
                        liveLocationDTO.latitude(),
                        liveLocationDTO.longitude())).thenReturn(2.5); // maior que a distância do threshold

                studentTravelRouteStopService.processRouteStopApproach(travelId, studentTravelId);

                verifyNoMoreInteractions(travelTrackingStaticCacheService, redisTrackingService, routeCalculationService);

                verifyNoInteractions(eventPublisher);
            }

            @ParameterizedTest
            @DisplayName("Deve ignorar o processamento do Ponto de Parada se o Status atual da Parada não for 'EXPECTED' ")
            @MethodSource("invalidRouteStopStatusProvider")
            void shouldNotPublishEventWhenRouteStopStatusIsNotExpected(StudentTravelRouteStopStatus invalidStudentTravelRouteStopStatus) {
                StudentTravelRouteStopTrackingCacheDTO cacheDataWithinInvalidStatus = new StudentTravelRouteStopTrackingCacheDTO(studentTravelId, UUID.randomUUID(), travelId, UUID.randomUUID(), -11.372, -32.111, TravelPeriod.EVENING, invalidStudentTravelRouteStopStatus, MONITORING_THRESHOLD);

                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(cacheDataWithinInvalidStatus);
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        cacheDTO.routeStopLatitude(),
                        cacheDTO.routeStopLongitude(),
                        liveLocationDTO.latitude(),
                        liveLocationDTO.longitude())).thenReturn(APPROACHING_THRESHOLD);

                studentTravelRouteStopService.processRouteStopApproach(travelId, studentTravelId);

                verifyNoMoreInteractions(travelTrackingStaticCacheService, redisTrackingService, routeCalculationService);

                verifyNoInteractions(eventPublisher);
            }

            public static Stream<Arguments> invalidRouteStopStatusProvider() {
                return Stream.of(
                        Arguments.of(StudentTravelRouteStopStatus.INVALID_ROUTE),
                        Arguments.of(StudentTravelRouteStopStatus.APPROACHING),
                        Arguments.of(StudentTravelRouteStopStatus.REACHED),
                        Arguments.of(StudentTravelRouteStopStatus.MISSED)
                );
            }

            @Test
            @DisplayName("Não deve realizar nenhum processamento dentro do Listener quando o evento recebido for null")
            void shouldNotProcessAnythingWhenEventIsNull() {
                processStudentTravelRouteStopApproachingListener.handleProcessRouteStopApproaching(null);

                verifyNoInteractions(redisTrackingService);
            }
        }

    }

    @Nested
    class confirmStudentRouteStopReached {
        UUID travelId;
        UUID studentTravelId;
        StudentTravelStatus studentTravelStatus;

        ConfirmStudentTravelRouteStopReachedEvent eventDTO;
        StudentTravelRouteStopTrackingCacheDTO cacheDTO;
        StudentTravelRouteStopDisembarkedEvent disembarkedEvent;
        StudentTravelRouteStopsCacheEvent monitoringCacheDTO;
        LiveLocationDTO liveLocationDTO;

        @BeforeEach
        void setUp() {
            travelId = UUID.randomUUID();
            studentTravelId = UUID.randomUUID();
            studentTravelStatus = StudentTravelStatus.AUTO_DISCONNECTED;

            cacheDTO = new StudentTravelRouteStopTrackingCacheDTO(studentTravelId, UUID.randomUUID(), travelId, UUID.randomUUID(), -11.372, -32.111, TravelPeriod.EVENING, StudentTravelRouteStopStatus.EXPECTED, MONITORING_THRESHOLD);
            eventDTO = new ConfirmStudentTravelRouteStopReachedEvent(studentTravelId, cacheDTO.studentId(), travelId, cacheDTO.routeStopId(), -11.323, -38.456, 3.0, Instant.parse("2026-08-24T23:49:33Z"), Instant.parse("2026-08-24T23:49:13Z"), studentTravelStatus);
            liveLocationDTO = new LiveLocationDTO(-12.9714, -38.5014, "_p~iF~ps|U_ulLnnqC_mqNvxq`@", 14250.50, -12.9700, -38.5000, Instant.parse("2026-08-24T23:49:33Z"));
            monitoringCacheDTO = new StudentTravelRouteStopsCacheEvent(studentTravelId, travelId, UUID.randomUUID(), 3.0, Instant.now(), -44.345, -72.345, StudentTravelRouteStopStatus.APPROACHING, 1.2, null, Instant.now(), -63.242, -70.132);
            disembarkedEvent = new StudentTravelRouteStopDisembarkedEvent(studentTravelId, eventDTO.routeStopId(), StudentTravelRouteStopStatus.REACHED, Instant.now(), Instant.now());
        }

        @Nested
        class successScenarios {
            @Test
            @DisplayName("Deve publicar os dois eventos (Confirmação e Desembarque) quando todas as validações anteriores forem atendidas")
            void shouldPublishBothConfirmationAndDisembarkedEventsWithSuccess() {
                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(cacheDTO);
                when(redisTrackingService.getStudentTravelRouteStopMonitoring(travelId, studentTravelId)).thenReturn(monitoringCacheDTO);
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(34.0);

                studentTravelRouteStopService.confirmStudentRouteStopReached(travelId, studentTravelId, studentTravelStatus);

                ArgumentCaptor<ConfirmStudentTravelRouteStopReachedEvent> confirmEventArgCaptor = ArgumentCaptor.forClass(ConfirmStudentTravelRouteStopReachedEvent.class);

                verify(eventPublisher, times(1)).publishEvent(confirmEventArgCaptor.capture());

                ConfirmStudentTravelRouteStopReachedEvent confirmEventArgCaptorValue = confirmEventArgCaptor.getValue();

                assertEquals(confirmEventArgCaptorValue.studentTravelId(), eventDTO.studentTravelId());
                assertEquals(confirmEventArgCaptorValue.travelId(), eventDTO.travelId());
                assertEquals(confirmEventArgCaptorValue.routeStopId(), eventDTO.routeStopId());
                assertEquals(34.0, confirmEventArgCaptorValue.distanceInMeters());
                assertEquals(studentTravelStatus, confirmEventArgCaptorValue.studentTravelStatus());

                ArgumentCaptor<StudentTravelRouteStopDisembarkedEvent> disembarkEventArgCaptor = ArgumentCaptor.forClass(StudentTravelRouteStopDisembarkedEvent.class);

                verify(eventPublisher, times(1)).publishEvent(disembarkEventArgCaptor.capture());

                StudentTravelRouteStopDisembarkedEvent disembarkEventArgCaptorValue = disembarkEventArgCaptor.getValue();

                assertEquals(disembarkEventArgCaptorValue.studentTravelRouteStopStatus(), disembarkedEvent.studentTravelRouteStopStatus());
                assertNotNull(disembarkEventArgCaptorValue.lastValidatedAt());
                assertNotNull(disembarkEventArgCaptorValue.reachedAt());

                verify(travelTrackingStaticCacheService, times(2)).getStudentTravelTrackingData(eq(travelId), eq(studentTravelId));
                verify(redisTrackingService, times(1)).getStudentTravelRouteStopMonitoring(eq(travelId), eq(studentTravelId));
                verify(redisTrackingService, times(1)).getLiveLocation(eq(travelId));
                verify(routeCalculationService, times(1)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());
            }

            @Test
            @DisplayName("Deve atualizar o monitoramento e limpar os caches do Redis ao receber o evento de confirmação")
            void shouldUpdateMonitoringAndClearCachesWhenConfirmationEventIsReceived() {
                confirmStudentTravelRouteStopListener.handleConfirmStudentTravelRouteStop(eventDTO);

                verify(redisTrackingService, times(1)).updateStudentTravelRouteStopConfirmMonitoring(argThat(updateCache ->
                        updateCache.studentTravelId().equals(eventDTO.studentTravelId()) &&
                                updateCache.studentId().equals(eventDTO.studentId()) &&
                                updateCache.vehicleLatitude().equals(eventDTO.vehicleLatitude()) &&
                                updateCache.distanceInMeters().equals(eventDTO.distanceInMeters()) &&
                                updateCache.studentTravelStatus().equals(eventDTO.studentTravelStatus()) &&
                                updateCache.disembarkAt() != null &&
                                updateCache.vehiclePositionAt() != null
                ));

                verify(travelTrackingStaticCacheService, times(1)).removeStudentTravelTrackingCache(eventDTO.travelId(), eventDTO.studentTravelId());

                verify(redisTrackingService, times(1)).deleteStudentTravelRouteStopMonitoring(eventDTO.travelId(), eventDTO.studentTravelId());

                verifyNoMoreInteractions(redisTrackingService, travelTrackingStaticCacheService);
            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve retornar silenciosamente sem publicar eventos quando os dados do cache estático de tracking forem null")
            void shouldReturnEarlyWithoutPublishWhenStaticTrackingCacheDataIsNull() {
                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(any(), any())).thenReturn(null);

                studentTravelRouteStopService.confirmStudentRouteStopReached(travelId, studentTravelId, studentTravelStatus);

                verify(travelTrackingStaticCacheService, times(1)).getStudentTravelTrackingData(travelId, studentTravelId);
                verify(eventPublisher, never()).publishEvent(any());
            }

            @Test
            @DisplayName("Deve retornar silenciosamente quando os dados de monitoriamento de aproximação não forem encontrados no redis")
            void shouldReturnEarlyWithoutPublishingWhenRouteStopMonitoringCacheIsNull() {
                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(cacheDTO);
                when(redisTrackingService.getStudentTravelRouteStopMonitoring(travelId, studentTravelId)).thenReturn(null);

                studentTravelRouteStopService.confirmStudentRouteStopReached(travelId, studentTravelId, studentTravelStatus);

                verify(travelTrackingStaticCacheService, times(1)).getStudentTravelTrackingData(travelId, studentTravelId);
                verify(eventPublisher, never()).publishEvent(any());
            }

            @ParameterizedTest
            @DisplayName("Deve retornar silenciosamente se o status atual da parada no cache não for APPROACHING")
            @MethodSource("invalidRouteStopStatusProvider")
            void shouldReturnEarlyWithoutPublishingWhenRouteStopStatusIsNotApproaching(StudentTravelRouteStopStatus routeStopStatus) {
                StudentTravelRouteStopsCacheEvent studentTravelRouteStopsCacheEvent = new StudentTravelRouteStopsCacheEvent(studentTravelId, travelId, UUID.randomUUID(), 3.0, Instant.now(), -44.345, -72.345, routeStopStatus, 1.2, null, Instant.now(), -63.242, -70.132);

                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(cacheDTO);
                when(redisTrackingService.getStudentTravelRouteStopMonitoring(any(), any())).thenReturn(studentTravelRouteStopsCacheEvent);

                studentTravelRouteStopService.confirmStudentRouteStopReached(travelId, studentTravelId, studentTravelStatus);

                verify(eventPublisher, never()).publishEvent(any());
            }

            public static Stream<Arguments> invalidRouteStopStatusProvider() {
                return Stream.of(
                        Arguments.of(StudentTravelRouteStopStatus.REACHED),
                        Arguments.of(StudentTravelRouteStopStatus.INVALID_ROUTE),
                        Arguments.of(StudentTravelRouteStopStatus.EXPECTED),
                        Arguments.of(StudentTravelRouteStopStatus.CANCELLED)
                );
            }

            @ParameterizedTest
            @DisplayName("Deve retornar silenciosamente se o status do estudante não indicar que ele saiu/foi desconectado")
            @MethodSource("invalidStudentTravelStatusProvider")
            void shouldReturnEarlyWithoutPublishingWhenStudentTravelStatusIsNotLeftOrAutoDisconnected(StudentTravelStatus invalidStudentTravelStatus) {
                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(cacheDTO);
                when(redisTrackingService.getStudentTravelRouteStopMonitoring(any(), any())).thenReturn(monitoringCacheDTO);

                studentTravelRouteStopService.confirmStudentRouteStopReached(travelId, studentTravelId, invalidStudentTravelStatus);

                verify(eventPublisher, never()).publishEvent(any());
            }

            public static Stream<Arguments> invalidStudentTravelStatusProvider() {
                return Stream.of(
                        Arguments.of(StudentTravelStatus.ACTIVE),
                        Arguments.of(StudentTravelStatus.AWAY_FROM_BUS)
                );
            }

            @Test
            @DisplayName("Deve retornar silenciosamente se a distância calculada for nula, negativa ou maior que o REACHED_THRESHOLD")
            void shouldReturnEarlyWithoutPublishingWhenDistanceIsInvalidOrExceedsReachedThreshold() {
                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(cacheDTO);
                when(redisTrackingService.getStudentTravelRouteStopMonitoring(any(), any())).thenReturn(monitoringCacheDTO);
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(REACHED_THRESHOLD + 10.0);

                studentTravelRouteStopService.confirmStudentRouteStopReached(travelId, studentTravelId, studentTravelStatus);

                verify(eventPublisher, never()).publishEvent(any());
            }

            @Test
            @DisplayName("Deve lidar corretamente com recebimento de evento null sem lançar exceptions ou executar o Listener")
            void shouldHandleNullEventCorrectlyWithoutCallingServices() {
                assertDoesNotThrow(() -> confirmStudentTravelRouteStopListener.handleConfirmStudentTravelRouteStop(null));

                verifyNoInteractions(redisTrackingService, travelTrackingStaticCacheService);
            }
        }
    }

    @Nested
    class cancelledStudentRouteStop {
        UUID travelId;
        UUID studentTravelId;
        UUID customerId;

        StudentTravelRouteStopTrackingCacheDTO cacheDTO;
        CancelledStudentTravelRouteStopEvent eventDTO;

        @BeforeEach
        void setUp() {
            travelId = UUID.randomUUID();
            studentTravelId = UUID.randomUUID();
            customerId = UUID.randomUUID();

            cacheDTO = new StudentTravelRouteStopTrackingCacheDTO(studentTravelId, UUID.randomUUID(), travelId, UUID.randomUUID(), -11.372, -32.111, TravelPeriod.EVENING, StudentTravelRouteStopStatus.EXPECTED, MONITORING_THRESHOLD);
            eventDTO = new CancelledStudentTravelRouteStopEvent(studentTravelId, cacheDTO.studentId(), travelId, cacheDTO.routeStopId(), customerId, StudentTravelRouteStopStatus.CANCELLED, Instant.now());
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve publicar o evento de cancelamento com dados corretos quando o cache é válido")
            void shouldPublishCancelledEventWithCorrectCacheData() {
                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId)).thenReturn(cacheDTO);

                studentTravelRouteStopService.cancelledStudentRouteStop(travelId, studentTravelId, customerId);

                ArgumentCaptor<CancelledStudentTravelRouteStopEvent> cancelledEventArgCaptor = ArgumentCaptor.forClass(CancelledStudentTravelRouteStopEvent.class);

                verify(eventPublisher, times(1)).publishEvent(cancelledEventArgCaptor.capture());

                CancelledStudentTravelRouteStopEvent eventArgCaptorValue = cancelledEventArgCaptor.getValue();

                assertEquals(eventArgCaptorValue.studentTravelId(), eventDTO.studentTravelId());
                assertEquals(eventArgCaptorValue.studentId(), eventDTO.studentId());
                assertEquals(eventArgCaptorValue.travelId(), eventDTO.travelId());
                assertEquals(eventArgCaptorValue.routeStopId(), eventDTO.routeStopId());
                assertEquals(eventArgCaptorValue.customerId(), eventDTO.customerId());
                assertEquals(StudentTravelRouteStopStatus.CANCELLED, eventArgCaptorValue.studentTravelRouteStopStatus());
                assertNotNull(eventArgCaptorValue.lastValidatedAt());

                verify(travelTrackingStaticCacheService, times(1)).getStudentTravelTrackingData(any(), any());
            }

            @Test
            @DisplayName("Deve atualizar o status no repositório, enviar notificação e limpar o cache ao receber o evento de cancelamento")
            void shouldUpdateRepositorySendNotificationAndClearCacheWhenCancelledEventIsReceived() {
                cancelledStudentTravelRouteStopListener.handleCancelledStudentTravelRouteStop(eventDTO);

                verify(studentTravelRouteStopRepository, times(1))
                        .updateCancelledStatus(eq(eventDTO.studentTravelId()), eq(eventDTO.routeStopId()), eq(eventDTO.studentTravelRouteStopStatus()), eq(eventDTO.lastValidatedAt()));

                verify(travelTrackingNotificationService, times(1))
                        .sendCancelledRouteStopNotification(argThat(notify -> notify.studentTravelId().equals(eventDTO.studentTravelId()) &&
                        notify.studentId().equals(eventDTO.studentId()) &&
                        notify.travelId().equals(eventDTO.travelId()) &&
                        notify.routeStopId().equals(eventDTO.routeStopId()) &&
                        notify.studentTravelRouteStopStatus().equals(eventDTO.studentTravelRouteStopStatus()) &&
                        notify.lastValidatedAt() != null));

                verify(travelTrackingStaticCacheService, times(1)).removeStudentTravelTrackingCache(eq(travelId), eq(studentTravelId));
            }
        }

        @Nested
        class failureScenarios {

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando parâmetros obrigatórios forem nulos")
            @MethodSource("nullRequireParametersProvider")
            void shouldThrowIllegalArgumentExceptionWhenRequireParametersAreNull(UUID invalidTravelId, UUID invalidStudentTravelId, UUID invalidCustomerId) {
                assertThrows(IllegalArgumentException.class, () -> studentTravelRouteStopService.cancelledStudentRouteStop(invalidTravelId, invalidStudentTravelId, invalidCustomerId));

                verify(travelTrackingStaticCacheService, never()).getStudentTravelTrackingData(any(), any());
                verify(eventPublisher, never()).publishEvent(any());
            }

            public static Stream<Arguments> nullRequireParametersProvider() {
                return Stream.of(
                        Arguments.of(null, UUID.randomUUID(), UUID.randomUUID()),
                        Arguments.of(UUID.randomUUID(), null, UUID.randomUUID()),
                        Arguments.of(UUID.randomUUID(), UUID.randomUUID(), null)
                );
            }

            @Test
            @DisplayName("Deve retornar silenciosamente quando os dados do Cache forem null")
            void shouldEarlyReturnWhenCacheDataAreNull() {
                when(travelTrackingStaticCacheService.getStudentTravelTrackingData(any(), any())).thenReturn(null);

                assertDoesNotThrow(() -> studentTravelRouteStopService.cancelledStudentRouteStop(travelId, studentTravelId, customerId));

                verify(travelTrackingStaticCacheService, times(1)).getStudentTravelTrackingData(any(), any());
                verify(eventPublisher, never()).publishEvent(any());

                verifyNoMoreInteractions(travelTrackingStaticCacheService);
            }

            @Test
            @DisplayName("Deve conseguir lidar com recebimento de evento null")
            void shouldDoNothingWhenEventReachedIsNull() {
                assertDoesNotThrow(() -> cancelledStudentTravelRouteStopListener.handleCancelledStudentTravelRouteStop(null));

                verifyNoInteractions(studentTravelRouteStopRepository, travelTrackingNotificationService, travelTrackingStaticCacheService);
            }
        }
    }
}