package com.travel_system.backend_app.integration.controller;

import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.enums.CitySize;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.*;
import com.travel_system.backend_app.service.MapboxAPIService;
import com.travel_system.backend_app.service.PolylineService;
import com.travel_system.backend_app.service.RedisTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TravelControllerIT extends IntegrationTestBase {

    @Autowired
    private TravelRepository travelRepository;
    @Autowired
    private StudentTravelRepository studentTravelRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private TravelReportsRepository travelReportsRepository;
    @Autowired
    private TravelLocationHistoryRepository travelLocationHistoryRepository;

    @Autowired
    private MapboxAPIService mapboxAPIService;
    @Autowired
    private RedisTrackingService redisTrackingService;
    @Autowired
    private PolylineService polylineService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // limpa a cada teste (obs: a ordem É IMPORTANTE)
        studentTravelRepository.deleteAll();
        travelRepository.deleteAll();
        studentRepository.deleteAll();
        driverRepository.deleteAll();
        travelReportsRepository.deleteAll();
        travelLocationHistoryRepository.deleteAll();
    }

    @Nested
    class createTravel {
        Driver driver;
        VehicleLocationRequestDTO requestDTO;
        TravelRequestDTO travelRequestDTO;

        @BeforeEach
        void setUp() {
            driver = new Driver(
                    null, "driver@test.com", "encoded_pass",
                    "João", "Silva", "71999999999",
                    null, GeneralStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(),
                    "Salvador", 0, new ArrayList<>());
            driverRepository.save(driver);

//            travel = new Travel(
//                    null, null, TravelStatus.TRAVELLING, driver,
//                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
//                    3600.0, 15000.0,
//                    -12.9714, -38.5016,
//                    -12.8000, -38.4000
//            );
//            travelRepository.save(travel);
//            travel.setStudentTravels(new HashSet<>());

            travelRequestDTO = new TravelRequestDTO(driver.getId(), -38.501200, -12.971800, -38.482300, -12.950400);
        }

        @Test
        @DisplayName("should create a new travel with success")
        void shouldCreateNewTravelWithSuccess() throws Exception {
            mockMvc.perform(post("/travel/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(travelRequestDTO)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.driverResponseDTO.id").value(driver.getId().toString()));

            List<Travel> travels = travelRepository.findAll();

            Travel firstTravelSaved = travels.getFirst();
            assertEquals(TravelStatus.PENDING, firstTravelSaved.getTravelStatus());
            assertEquals(driver.getId(), firstTravelSaved.getDriver().getId());
            assertNotNull(firstTravelSaved.getStartHourTravel());
        }

        @Test
        void throwExceptionWhenDriverIsInactive() throws Exception {
            driver.setStatus(GeneralStatus.INACTIVE);
            driverRepository.save(driver);

            mockMvc.perform(post("/travel/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(travelRequestDTO)))
                    .andExpect(status().isBadRequest());

        }

        @Test
        @DisplayName("throw exception when Travel has TravelStatus 'PENDING' or 'TRAVELLING' ")
        void throwExceptionWhenTravelAlreadyUnderway() throws Exception {
            Travel travel = new Travel(
                    null, null, TravelStatus.TRAVELLING, driver,
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000
            );
            travelRepository.save(travel);

            mockMvc.perform(post("/travel/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(travelRequestDTO)))
                    .andExpect(status().isConflict());

            boolean result = travelRepository.existsByDriverIdAndTravelStatusIn(driver.getId(), List.of(TravelStatus.PENDING, TravelStatus.TRAVELLING));

            assertTrue(result);
        }
    }
}
