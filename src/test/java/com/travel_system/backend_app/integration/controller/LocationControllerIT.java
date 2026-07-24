package com.travel_system.backend_app.integration.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveCoordinates;
import com.travel_system.backend_app.model.enums.ClientSector;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.repository.GeoPositionRepository;
import com.travel_system.backend_app.repository.StudentRepository;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.service.RouteCalculationService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class LocationControllerIT extends IntegrationTestBase {

    @MockitoBean
    private RouteCalculationService routeCalculationService;

    @Autowired
    private StudentTravelRepository studentTravelRepository;

    @Autowired
    private GeoPositionRepository geoPositionRepository;

    @Autowired
    private StudentRepository studentRepository;

    private final String PATH_CONTROLLER = "/v1/location";
    private final String AUTH_USER = "authenticated_user";

    @Nested
    @Transactional
    class studentPosition {
        Student student;
        StudentTravel studentTravel;
        LiveCoordinates liveCoordinates;
        GeoPosition geoPosition;

        @BeforeEach
        void setUp() {
            student = new Student(
                    null,
                    "ana.souza@exemplo.com",
                    "Senha@123",
                    "Ana",
                    "Souza",
                    "+55 11 99999-1234",
                    "https://cdn.exemplo.com/students/ana-souza.png",
                    GeneralStatus.ACTIVE,
                    LocalDateTime.of(2026, 7, 16, 12, 0),
                    LocalDateTime.of(2026, 7, 16, 12, 0),
                    null,
                    InstitutionType.UNIVERSITY,
                    "Engenharia de Software"
            );
            studentRepository.save(student);

            studentTravel = new StudentTravel(null, null, student, true, Instant.now().minusSeconds(20), null, null, null);
            studentTravelRepository.save(studentTravel);

            geoPosition = new GeoPosition(null, 37.7749, -122.4194, Instant.now(), null);
            geoPositionRepository.save(geoPosition);

            liveCoordinates = new LiveCoordinates(-12.97000, -38.50000);
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve criar (persistir) a primeira posição do estudante seguindo a sua posição atual")
            void shouldCreateFirstGeoPositionWhenStudentSendsFirstLocation() throws Exception {
                mockMvc.perform(post(PATH_CONTROLLER + "/" + studentTravel.getId()).with(user(AUTH_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(liveCoordinates)))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                verify(routeCalculationService, never()).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());

                StudentTravel persisted = studentTravelRepository.findById(studentTravel.getId()).orElseThrow();

                assertNotNull(persisted.getPosition());
                assertEquals(liveCoordinates.latitude(), persisted.getPosition().getLatitude());
                assertEquals(liveCoordinates.longitude(), persisted.getPosition().getLongitude());
                assertNotNull(persisted.getPosition().getTimeStamp());
                assertEquals(persisted.getId(), persisted.getPosition().getStudentTravel().getId());
            }

            @Test
            @DisplayName("Deve validar que a posição existente no banco é atualizada quando houver deslocamento maior que o permitido")
            void shouldUpdateGeoPositionWhenStudentMovesMoreThanThreeMeters() throws Exception {
                // setup básico com as posições armazenadas
                studentTravel.setPosition(geoPosition);
                studentTravelRepository.save(studentTravel);

                geoPosition.setStudentTravel(studentTravel);
                geoPositionRepository.save(geoPosition);

                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(4.0); // maior que o limite permitido

                mockMvc.perform(post(PATH_CONTROLLER + "/" + studentTravel.getId()).with(user(AUTH_USER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(liveCoordinates)))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                verify(routeCalculationService, times(1)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());

                StudentTravel persisted = studentTravelRepository.findById(studentTravel.getId()).orElseThrow();

                assertEquals(persisted.getPosition().getLongitude(), liveCoordinates.longitude());
                assertEquals(persisted.getPosition().getLatitude(), liveCoordinates.latitude());

                assertNotNull(persisted.getPosition().getTimeStamp());

                assertEquals(persisted.getPosition().getId(), geoPosition.getId());
            }

            @Test
            @DisplayName("Deve validar que pequenas variações inferiores à tolerância mínima não atualizem a posição")
            void shouldKeepCurrentGeoPositionWhenStudentDoesNotMoveEnough() throws Exception {
                // setup básico com as posições armazenadas
                studentTravel.setPosition(geoPosition);
                studentTravelRepository.save(studentTravel);

                geoPosition.setStudentTravel(studentTravel);
                geoPositionRepository.save(geoPosition);

                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(2.0); // menor que o limite permitido

                mockMvc.perform(post(PATH_CONTROLLER + "/" + studentTravel.getId()).with(user(AUTH_USER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(liveCoordinates)))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                verify(routeCalculationService, times(1)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());

                StudentTravel persisted = studentTravelRepository.findById(studentTravel.getId()).orElseThrow();

                assertEquals(persisted.getPosition().getLatitude(), geoPosition.getLatitude());
                assertEquals(persisted.getPosition().getLongitude(), geoPosition.getLongitude());
                assertEquals(persisted.getPosition().getTimeStamp(), geoPosition.getTimeStamp());
            }

            @ParameterizedTest
            @DisplayName("Deve retornar de forma silenciosa caso os dados de latitude/longitude são null")
            @MethodSource("invalidLiveCoordinatesProvider")
            void shouldIgnoreRequestWhenLatitudeOrLongitudeIsNull(LiveCoordinates invalidLiveCoords) throws Exception {
                mockMvc.perform(post(PATH_CONTROLLER + "/" + studentTravel.getId()).with(user(AUTH_USER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidLiveCoords)))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                verify(routeCalculationService, never()).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());
            }

            public static Stream<Arguments> invalidLiveCoordinatesProvider() {
                return Stream.of(
                        Arguments.of(new LiveCoordinates(null, -38.50000)),
                        Arguments.of(new LiveCoordinates(-12.97000, null))
                );
            }

            @Test
            @DisplayName("Deve garantir que o sistema está reutilizando a entidade existente sem criar uma nova ")
            void shouldUpdateExistingGeoPositionInsteadOfCreatingAnotherOne() throws Exception {
                // setup básico com as posições armazenadas
                studentTravel.setPosition(geoPosition);
                studentTravelRepository.save(studentTravel);

                geoPosition.setStudentTravel(studentTravel);
                geoPositionRepository.save(geoPosition);

                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(4.0); // maior que o limite permitido

                mockMvc.perform(post(PATH_CONTROLLER + "/" + studentTravel.getId()).with(user(AUTH_USER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(liveCoordinates)))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                verify(routeCalculationService, times(1)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());

                StudentTravel persisted = studentTravelRepository.findById(studentTravel.getId()).orElseThrow();

                assertEquals(1, geoPositionRepository.count()); // continua com apenas um registro no banco de dados
                assertEquals(persisted.getPosition().getId(), geoPosition.getId()); // id tbm se mantém
            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o vínculo estudante-viagem não existir")
            void shouldReturnNotFoundWhenStudentTravelDoesNotExist() throws Exception {
                UUID randomStudentTravelId = UUID.randomUUID();

                mockMvc.perform(post(PATH_CONTROLLER + "/" + randomStudentTravelId).with(user(AUTH_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(liveCoordinates)))
                        .andExpect(status().isNotFound());
            }

            @Test
            @DisplayName("Deve validar a desserialização da requisição do DTO, lançando exception")
            void shouldReturnBadRequestWhenRequestBodyIsMalformed() throws Exception {
                String invalidJson = """
                    {
                        "latitude": "abcd",
                        "longitude": -38.5000
                    }
                    """;

                mockMvc.perform(post(PATH_CONTROLLER + "/" + studentTravel.getId()).with(user(AUTH_USER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidJson))
                        .andExpect(status().isBadRequest());

            }

            @Test
            @DisplayName("Deve lançar exception quando nenhum body for enviado na requisição")
            void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
                mockMvc.perform(post(PATH_CONTROLLER + "/" + studentTravel.getId()).with(user(AUTH_USER))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest());
            }
        }
    }
}
