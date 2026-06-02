package com.travel_system.backend_app.integration.controller;

import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.model.GeoPosition;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveCoordinates;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.repository.GeoPositionRepository;
import com.travel_system.backend_app.repository.StudentRepository;
import com.travel_system.backend_app.repository.StudentTravelRepository;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class LocationControllerIT extends IntegrationTestBase {

    @Autowired
    private StudentTravelRepository studentTravelRepository;

    @Autowired
    private GeoPositionRepository geoPositionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Nested
    @Transactional
    class studentPosition {
        StudentTravel studentTravel;
        LiveCoordinates liveCoordinates;

        @BeforeEach
        void setUp() {
            Student student = new Student(
                    null,
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
            studentRepository.save(student);

            studentTravel = new StudentTravel(null, null, student, true, Instant.now().minusSeconds(20), null, null, null);
            studentTravelRepository.save(studentTravel);

            liveCoordinates = new LiveCoordinates(-12.97000, -38.50000);
        }

        @Test
        @DisplayName("when StudentTravel has no position, should create a new position, persist and returns 204")
        void shouldCrateNewGeoPositionWhenStudentTravelHasNoPosition() throws Exception {
            mockMvc.perform(post("/v1/location/{studentTravelId}", studentTravel.getId())
                            .with(user("auth_user"))
                            .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(liveCoordinates)))
                        .andDo(print())
                        .andExpect(status().isNoContent());

            List<GeoPosition> geoPositionsList = geoPositionRepository.findAll();

            assertEquals(1, geoPositionsList.size());

            GeoPosition savedGeoPosition = geoPositionsList.getFirst();

            assertEquals(-12.97000, savedGeoPosition.getLatitude());
            assertEquals(-38.50000, savedGeoPosition.getLongitude());

            assertNotNull(savedGeoPosition.getTimeStamp());
        }

        @Test
        @DisplayName("when student has displacement, should update ")
        void shouldUpdatePositionWhenStudentHasDisplacement() throws Exception {
            // posição anterior
            GeoPosition previousPosition = new GeoPosition();
            previousPosition.setLatitude(-12.97000);
            previousPosition.setLongitude(-38.50000);
            previousPosition.setTimeStamp(Instant.now());
            previousPosition.setStudentTravel(studentTravel);
            geoPositionRepository.save(previousPosition);

            studentTravel.setPosition(previousPosition);
            studentTravelRepository.save(studentTravel);

            LiveCoordinates displacementPos = new LiveCoordinates(-12.99001, -38.70001);

            mockMvc.perform(post("/v1/location/{studentTravelId}", studentTravel.getId())
                            .with(user("auth_user"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(displacementPos)))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            // position deve ter sido atualizada
            GeoPosition result = geoPositionRepository.findById(previousPosition.getId()).orElseThrow();
            assertEquals(-12.99001, result.getLatitude());
            assertEquals(-38.70001, result.getLongitude());

            assertNotNull(result.getTimeStamp());

            // verifica se o studentTravel que foi salvo é o mesmo
            StudentTravel studentTravelResult = studentTravelRepository.findById(studentTravel.getId()).orElseThrow();
            assertEquals(result.getStudentTravel().getId(), studentTravelResult.getId());
        }

        @Test
        @DisplayName("when student has no displacement, should do nothing and returns")
        void shouldNotUpdatePositionWhenStudentHasNoDisplacement() throws Exception {
            // posição anterior
            GeoPosition previousPosition = new GeoPosition();
            previousPosition.setLatitude(-12.97000);
            previousPosition.setLongitude(-38.50000);
            previousPosition.setTimeStamp(Instant.now());
            previousPosition.setStudentTravel(studentTravel);
            geoPositionRepository.save(previousPosition);

            studentTravel.setPosition(previousPosition);
            studentTravelRepository.save(studentTravel);

            LiveCoordinates samePosition = new LiveCoordinates(-12.97001, -38.50001);

            mockMvc.perform(post("/v1/location/{studentTravelId}", studentTravel.getId())
                            .with(user("auth_user"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(samePosition)))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            // position deve ter sido atualizada
            GeoPosition result = geoPositionRepository.findById(previousPosition.getId()).orElseThrow();
            assertEquals(-12.97000, result.getLatitude());
            assertEquals(-38.50000, result.getLongitude());
        }

        @ParameterizedTest
        @MethodSource("nullRequireCoordsProvider")
        void shouldReturnNullSilentlyWhenRequireCoordsDataAreNull(LiveCoordinates coordinates) throws Exception {
            mockMvc.perform(post("/v1/location/{studentTravelId}", studentTravel.getId())
                            .with(user("auth_user"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(coordinates)))
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }

        public static Stream<Arguments> nullRequireCoordsProvider() {
            return Stream.of(
                    Arguments.of(new LiveCoordinates(null, -38.50000)),
                    Arguments.of(new LiveCoordinates(-12.97000, null))
            );
        }

        @Test
        void throwExceptionWhenStudentTravelEntityNotFound() throws Exception {
            mockMvc.perform(post("/v1/location/{studentTravelId}", UUID.randomUUID())
                            .with(user("auth_user"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(liveCoordinates)))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }

    }
}
