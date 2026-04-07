package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.repository.TravelRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.junit.rules.ExternalResource.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MapboxAPIServiceTest {
    /*
     * PADRÕES DOS TESTES UNITÁRIOS
     * 1. TESTAR TODOS AS SAÍDAS (RESULTADOS) DOS MÉTODOS EM QUESTÃO
     * 2. OS MÉTODOS SUCCESS DEVEM CONTER "WithSuccess"
     * 3. OS MÉTODOS FAILURE DEVEM CONTER "ThrowException"
     * 4. MÉTODOS COM VÁRIAS POSSÍVEIS SAÍDAS DEVEM SER OBRIGATORIAMENTE ENGLOBADAS EM CLASSES PRÓPRIAS DE SUCCESS E FAILURE (MESMO DENTRO DA SUA CLASSE DE ORIGEM)
     * 5. NUNCA USAR RUNTIME EX. COMO EXCEÇÃO CORINGA, USE A PRÓPRIA EXCEÇÃO LANÇADA NO MÉTODO
     * 6. SEMPRE ADICIONAR UMA BREVE DESCRIÇÃO COM A ANNOTATION '@DisplayName("...")'.
     * 7. OS TESTES DEVEM OBRIGATORIAMENTE SEGUIR O PADRÃO AAA (ARRANGE, ACT & ASSERT)
     */

    static MockWebServer mockWebServer;

    @InjectMocks
    private MapboxAPIService mapboxAPIService;

    @Mock
    private TravelRepository travelRepository;

    private ArgumentCaptor<Travel> travelArgCaptor = ArgumentCaptor.forClass(Travel.class);

    @BeforeAll
    static void startServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockWebServer.shutdown();
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        // aponta o webclient para o servidor fake
        WebClient webClient = WebClient.builder().
                baseUrl(mockWebServer.url("/").toString())
                .build();

        mapboxAPIService = new MapboxAPIService(webClient, travelRepository);
        ReflectionTestUtils.setField(mapboxAPIService, "accessToken", "fake-token");
    }

    @Nested
    class calculateRoute {

        @Test
        @DisplayName("should return All API data with success")
        void shouldReturnAllApiDataWithSuccess() {
            // arrange - define o servidor fake
            String fakeResponse = """
                    {
                        "code": "Ok",
                        "uuid": "123bc",
                        "waypoints": [],
                        "routes": [{
                            "duration": 1200.7,
                            "distance": 4130.3,
                            "geometry": "polyline_decoded_string"
                        }]
                    }
                    """;

            mockWebServer.enqueue(new MockResponse()
                    .setBody(fakeResponse)
                    .addHeader("Content-Type", "application/json"));

            // act
            RouteDetailsDTO result = mapboxAPIService.calculateRoute(-38.5014, -12.9714, -38.4500, -12.9000);

            // assert
            assertNotNull(result, "api response must never be null");

            assertEquals(1201.0, result.duration()); // math.round arredondando
            assertEquals(4130.0, result.distance()); // math.round arredondando
            assertEquals("polyline_decoded_string", result.geometry());
        }

        @Test
        @DisplayName("verification request sent to the server")
        void verificationRequestSentToTheServer() throws InterruptedException {
            // arrange - define o servidor fake
            String fakeResponse = """
                    {
                        "code": "Ok",
                        "uuid": "123bc",
                        "waypoints": [],
                        "routes": [{
                            "duration": 1200.7,
                            "distance": 4130.3,
                            "geometry": "polyline_decoded_string"
                        }]
                    }
                    """;

            mockWebServer.enqueue(new MockResponse()
                    .setBody(fakeResponse)
                    .addHeader("Content-Type", "application/json"));

            // act
            RouteDetailsDTO result = mapboxAPIService.calculateRoute(-38.5014, -12.9714, -38.4500, -12.9000);

            // aaserts
            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            String path = recordedRequest.getPath();

            assertNotNull(path);
            assertTrue(path.contains("/mapbox/driving"));
            assertTrue(path.contains("geometries=polyline"));
            assertTrue(path.contains("overview=full"));
            assertTrue(path.contains("access_token=fake-token"));
            assertEquals("GET", recordedRequest.getMethod());

            assertNotNull(result);
        }

        @Test
        @DisplayName("should return silently if routes list are null")
        void shouldReturnSilentlyIfRoutesListAreNull() {
            // arrange - define o servidor fake sem o routes
            String fakeResponse = """
                    {
                        "code": "Ok",
                        "uuid": "123bc",
                        "waypoints": [],
                        "routes": []
                    }
                    """;

            mockWebServer.enqueue(new MockResponse().setBody(fakeResponse)
                    .addHeader("Content-Type", "application/json"));

            // act
            RouteDetailsDTO result = mapboxAPIService.calculateRoute(-38.5014, -12.9714, -38.4500, -12.9000);

            // assert
            assertNull(result);
        }

        @Test
        @DisplayName("throw exception when api returns an http error 4xx or 5xx")
        void throwExceptionWhenApiReturnsAnHttpError() {
            // arrange - define o servidor fake
            String fakeResponse = """
                    {
                        "code": "Ok",
                        "uuid": "123bc",
                        "waypoints": [],
                        "routes": [{
                            "duration": 1200.7,
                            "distance": 4130.3,
                            "geometry": "polyline_decoded_string"
                        }]
                    }
                    """;

            mockWebServer.enqueue(new MockResponse()
                    .setBody(fakeResponse)
                    .setResponseCode(500));


            // act & assert
            assertThrows(WebClientResponseException.class, () -> mapboxAPIService.calculateRoute(-38.5014, -12.9714, -38.4500, -12.9000));
        }

        @ParameterizedTest
        @DisplayName("should return null when require fields are null")
        @MethodSource("nullRequiredFieldsProvier")
        void shouldSilentlyReturnNullWhenRequireFieldsAreNull(Double originLong, Double originLat, Double destLong, Double destLat) {
            int countBefore = mockWebServer.getRequestCount();

            RouteDetailsDTO result = mapboxAPIService.calculateRoute(originLong, originLat, destLong, destLat);

            assertNull(result);
            int countAfter = mockWebServer.getRequestCount();
            assertEquals(0, countAfter - countBefore); // nenhuma nova request foi feita
        }

        public static Stream<Arguments> nullRequiredFieldsProvier() {
            return Stream.of(
              Arguments.of(null, -12.9714, -38.4500, -12.9000),
              Arguments.of(-38.5014, null, -38.4500, -12.9000),
              Arguments.of(-38.5014, -12.9714, null, -12.9000),
              Arguments.of(-38.5014, -12.9714, -38.4500, null)
            );
        }
    }

    @Nested
    class recalculateETA {

        @Test
        @DisplayName("should return the distance and remaining time based on actual location with success")
        void shouldRecalculateETAWithSuccess() {
            // arrange - prepara para o servidor fake
            String fakeResponse = """
                    {
                        "code": "Ok",
                        "uuid": "123bc",
                        "waypoints": [],
                        "routes": [{
                            "duration": 1200.7,
                            "distance": 4130.3,
                            "geometry": "polyline_decoded_string"
                        }]
                    }
                    """;

            mockWebServer.enqueue(new MockResponse().setBody(fakeResponse)
                    .setHeader("Content-Type", "application/json"));

            // act
            RouteDetailsDTO result = mapboxAPIService.recalculateETA(-38.5014, -12.9714, -38.4500, -12.9000);

            // asserts
            assertNotNull(result);

            assertEquals(1201.0, result.duration()); // math.round
            assertEquals(4130.0, result.distance()); // math.round
            assertEquals("polyline_decoded_string", result.geometry());
        }

        @ParameterizedTest
        @DisplayName("should return silently if any require coords data are null")
        @MethodSource("nullFieldsProvider")
        void shouldReturnSilentlyWhenRequireCoordsDataAreNull(Double currentLng, Double currentLat, Double finalLong, Double finalLat) {
            RouteDetailsDTO result = mapboxAPIService.recalculateETA(currentLng, currentLat, finalLong, finalLat);

            assertNull(result);
        }

        public static Stream<Arguments> nullFieldsProvider() {
            return Stream.of(
                    Arguments.of(null, -12.9714, -38.4500, -12.9000),
                    Arguments.of(-38.5014, null, -38.4500, -12.9000),
                    Arguments.of(-38.5014, -12.9714, null, -12.9000),
                    Arguments.of(-38.5014, -12.9714, -38.4500, null)
            );
        }
    }

    @Nested
    class getRouteDetailsDTO {

        @Test
        @DisplayName("should get and save route details data in entity travel with success")
        void shouldGetAndSaveRouteDetailsInEntityTravelWithSuccess() {
            // arrange
            String fakeResponse = """
                    {
                        "code": "Ok",
                        "uuid": "123bc",
                        "waypoints": [],
                        "routes": [{
                            "duration": 1200.7,
                            "distance": 4130.3,
                            "geometry": "polyline_decoded_string"
                        }]
                    }
                    """;

            mockWebServer.enqueue(new MockResponse().setBody(fakeResponse)
                    .addHeader("Content-Type", "application/json"));

            // act
            mapboxAPIService.getAndSaveRouteDetailsDTO(-38.5014, -12.9714, -38.4500, -12.9000);

            // asserts
            verify(travelRepository, times(1)).save(travelArgCaptor.capture());
            Travel savedTravel = travelArgCaptor.getValue();

            assertEquals(1201.0, savedTravel.getDuration());
            assertEquals(4130.0, savedTravel.getDistance());
            assertEquals("polyline_decoded_string", savedTravel.getPolylineRoute());

            verifyNoMoreInteractions(travelRepository);
        }

        @ParameterizedTest
        @DisplayName("should return silently if any require coords data are null")
        @MethodSource("nullFieldsProvider")
        void shouldReturnSilentlyWhenRequireCoordsDataAreNull(Double currentLng, Double currentLat, Double finalLong, Double finalLat) {
            mapboxAPIService.getAndSaveRouteDetailsDTO(currentLng, currentLat, finalLong, finalLat);

            verify(travelRepository, never()).save(any());
        }

        public static Stream<Arguments> nullFieldsProvider() {
            return Stream.of(
                    Arguments.of(null, -12.9714, -38.4500, -12.9000),
                    Arguments.of(-38.5014, null, -38.4500, -12.9000),
                    Arguments.of(-38.5014, -12.9714, null, -12.9000),
                    Arguments.of(-38.5014, -12.9714, -38.4500, null)
            );
        }
    }
}