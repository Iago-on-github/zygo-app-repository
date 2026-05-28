package com.travel_system.backend_app.integration.controller;

import com.google.api.client.json.Json;
import com.travel_system.backend_app.config.RabbitMQConfig;
import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.GpsPayload;
import com.travel_system.backend_app.model.enums.CitySize;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.CityRepository;
import com.travel_system.backend_app.repository.DriverRepository;
import com.travel_system.backend_app.repository.PermissionsRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class GpsDataControllerIT extends IntegrationTestBase {

    @MockitoBean
    private TravelRepository travelRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private PermissionsRepository permissionsRepository;

    @Autowired
    private CityRepository cityRepository;

    @Nested
    class sendVehicleGps {
        Travel travel;
        VehicleLocationRequestDTO requestDTO;
        City city;
        Driver driver;

        @BeforeEach
        void setUp() {
            Permissions permission = new Permissions("ROLE_DRIVER");
            permissionsRepository.save(permission);

            city = new City(null, "Salvador", CitySize.TOWN, true);
            cityRepository.save(city);

            driver = new Driver(
                    null, "driver@test.com", "encoded_pass",
                    "João", "Silva", "71999999999",
                    null, GeneralStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(),
                    "Salvador", 0, new ArrayList<>(), new City());
            driver.setPermissions(List.of(permission));
            driverRepository.save(driver);

            travel = new Travel(
                    UUID.randomUUID(), city, TravelStatus.TRAVELLING, driver, Instant.now(),
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000, "Feira de Santana"
            );

//            travelRepository.save(travel);

            requestDTO = new VehicleLocationRequestDTO(travel.getId(), -12.9750, -38.5020, 60.0, 180.0);
        }

        @Test
        void shouldReturnAcceptedWhenTravelExistsAndStatusIsTravellingAndSendDataToRabbitMQ() throws Exception {
            String driverRole = driver.getRoles().getFirst();

            when(travelRepository.existsByIdAndTravelStatus(travel.getId(), TravelStatus.TRAVELLING))
                    .thenReturn(true);

            mockMvc.perform(post("/api/v1/gps/updateGpsData").with(user("driver").authorities(new SimpleGrantedAuthority(driverRole)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestDTO))
                    .param("city", city.getId().toString())
                    .param("travelId", travel.getId().toString()))
                    .andDo(print())
                    .andExpect(status().isAccepted());

            String expectedRoutingKey = "v1.gps." + city.getId() + "." + travel.getId();

            verify(rabbitTemplate, times(1)).convertAndSend(
                    eq(RabbitMQConfig.EXCHANGE_GPS_NAME),
                    eq(expectedRoutingKey),
                    any(GpsPayload.class),
                    any(MessagePostProcessor.class));
        }

        @Test
        void shouldReturnForbiddenWhenHasNoAuthentication() throws Exception {
            when(travelRepository.existsByIdAndTravelStatus(travel.getId(), TravelStatus.TRAVELLING))
                    .thenReturn(true);

            // envia sem nenhuma role de auth
            mockMvc.perform(post("/api/v1/gps/updateGpsData")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDTO))
                            .param("city", city.getId().toString())
                            .param("travelId", travel.getId().toString()))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 403 when req has a different role")
        void shouldReturnForbiddenWhenRequisitionHasDifferentRole() throws Exception {
            String differentRole = "ROLE_STUDENT";

            when(travelRepository.existsByIdAndTravelStatus(travel.getId(), TravelStatus.TRAVELLING))
                    .thenReturn(true);

            mockMvc.perform(post("/api/v1/gps/updateGpsData")
                            .with(user("driver").authorities(new SimpleGrantedAuthority(differentRole)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDTO))
                            .param("city", city.getId().toString())
                            .param("travelId", travel.getId().toString()))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturnNotFoundWhenTripNotFound() throws Exception {
            String driverRole = driver.getRoles().getFirst();

            when(travelRepository.existsByIdAndTravelStatus(travel.getId(), TravelStatus.TRAVELLING))
                    .thenReturn(false);

            mockMvc.perform(post("/api/v1/gps/updateGpsData").with(user("driver").authorities(new SimpleGrantedAuthority(driverRole)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDTO))
                            .param("city", city.getId().toString())
                            .param("travelId", travel.getId().toString()))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }

        @ParameterizedTest
        @MethodSource("travelStatusProvider")
        void shouldReturnNotFoundWhenTripIsNotTravelling(TravelStatus travelStatus) throws Exception {
            String driverRole = driver.getRoles().getFirst();

            when(travelRepository.existsByIdAndTravelStatus(travel.getId(), travelStatus))
                    .thenReturn(false);

            mockMvc.perform(post("/api/v1/gps/updateGpsData").with(user("driver").authorities(new SimpleGrantedAuthority(driverRole)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDTO))
                            .param("city", city.getId().toString())
                            .param("travelId", travel.getId().toString()))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }

        public static Stream<Arguments> travelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.PENDING),
                    Arguments.of(TravelStatus.FINISH)
            );
        }

        @ParameterizedTest
        @MethodSource("nullRequireParametersProvider")
        void shouldReturnBadRequestWhenRequireParametersAreNull(String cityId, String travelId, VehicleLocationRequestDTO locationRequestDTO) throws Exception {
            String driverRole = driver.getRoles().getFirst();

            mockMvc.perform(post("/api/v1/gps/updateGpsData").with(user("driver").authorities(new SimpleGrantedAuthority(driverRole)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(locationRequestDTO))
                    .param("city", cityId)
                    .param("travelId", travelId))
                    .andExpect(status().isBadRequest());
        }

        public static Stream<Arguments> nullRequireParametersProvider() {
            return Stream.of(
                    Arguments.of(null, UUID.randomUUID().toString(), new VehicleLocationRequestDTO(UUID.randomUUID(), -12.9750, -38.5020, 60.0, 180.0)),
                    Arguments.of(UUID.randomUUID().toString(), null, new VehicleLocationRequestDTO(UUID.randomUUID(), -12.9750, -38.5020, 60.0, 180.0)),
                    Arguments.of(UUID.randomUUID().toString(), UUID.randomUUID().toString(), null)
            );
        }
    }
}
