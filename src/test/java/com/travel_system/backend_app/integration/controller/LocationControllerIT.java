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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
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

            studentTravel = new StudentTravel(null, null, student, true, Instant.now().minusSeconds(20), null, null);
            studentTravelRepository.save(studentTravel);

            liveCoordinates = new LiveCoordinates(-12.97000, -38.50000);
        }

        @Test
        @DisplayName("when StudentTravel has no position, should create a new position, persist and returns 204")
        void shouldCrateNewGeoPositionWhenStudentTravelHasNoPosition() throws Exception {
            mockMvc.perform(post("/location/{studentTravelId}", studentTravel.getId())
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
    }
}
