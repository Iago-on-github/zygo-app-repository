package com.travel_system.backend_app.integration.controller;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.StandardRoute;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.request.RouteDeviationRequestDTO;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.TravelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.shaded.org.checkerframework.checker.units.qual.C;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class RouteCalculationControllerIT extends IntegrationTestBase {

    @Autowired
    private TravelRepository travelRepository;

/*    @Nested
    class isRouteDeviation {
        RouteDeviationRequestDTO routeDeviationRequestDTO;
        Travel travel;

        @BeforeEach
        void setUp() {
            travel = new Travel(UUID.randomUUID(), TravelStatus.PENDING, new Driver(), Instant.now(), Instant.now(), TravelPeriod.MORNING, null, "encodedPolyline", 35.5, 18.2, -12.9714, -38.5014, -12.9800, -38.4900, "Salvador", new Customer(), new StandardRoute());

            travelRepository.save(travel);

            routeDeviationRequestDTO = new RouteDeviationRequestDTO(travel.getId(), -12.96900, -38.49900);
        }

        @Test
        @DisplayName("Should return false when driver is within 50 meters route tolerance")
        void shouldReturnFalseWhenDriverIsWithinRouteTolerance() throws Exception {
            String polylineRoute = "route_test_offset";

            // Rota simplificada: dois pontos próximos (≈ 100 metros de distância entre si)
            Point routeStart = Point.fromLngLat(-38.50000, -12.97000);
            Point routeEnd = Point.fromLngLat(-38.49900, -12.96900);
            List<Point> mockedRoute = List.of(routeStart, routeEnd);

            RouteDeviationRequestDTO driverFarAwayDTO = new RouteDeviationRequestDTO(
                    travel.getId(),
                    -13.50000,
                    -38.50000
            );

            when(polylineService.formattedPolylineDecoded(polylineRoute)).thenReturn(mockedRoute);

            mockMvc.perform(post("/v1/route/deviation")
                            .with(user("authenticated_user"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(driverFarAwayDTO)))
                        .andDo(print())
                        .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return true when driver exceeds 50 meters route tolerance")
        void shouldReturnTrueWhenDriverIsOutsideRouteTolerance() throws Exception {
            String polylineRoute = "route_test_offset";
            travel.setPolylineRoute(polylineRoute);
            travelRepository.save(travel);

            Point routeStart = Point.fromLngLat(-38.50000, -12.97000);
            Point routeEnd   = Point.fromLngLat(-38.50000, -12.96000);

            when(polylineService.formattedPolylineDecoded(polylineRoute))
                    .thenReturn(List.of(routeStart, routeEnd));

            RouteDeviationRequestDTO driverFarAwayDTO = new RouteDeviationRequestDTO(
                    travel.getId(),
                    -12.96500,
                    -39.50000
            );

            mockMvc.perform(post("/v1/route/deviation")
                            .with(user("authenticated_user"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(driverFarAwayDTO)))
                    .andDo(print())
                    .andExpect(status().isOk())

                    .andExpect(jsonPath("$.isOffRoute").value(true))

                    .andExpect(jsonPath("$.distanceToRouteMeters").exists())
                    .andExpect(jsonPath("$.distanceToRouteMeters").value(org.hamcrest.Matchers.greaterThan(50.0)))

                    .andExpect(jsonPath("$.nearestPointLat").exists())
                    .andExpect(jsonPath("$.nearestPointLng").exists());
        }

        @ParameterizedTest
        @MethodSource("missingParametersProvider")
        @DisplayName("Should return 200 ok and 'isRouteOff' false when DTO or any required field inside RequestBody is missing")
        void shouldReturnBadRequestWhenRequiredFieldsAreMissing(RouteDeviationRequestDTO routeDeviationRequestDTO) throws Exception {
            MockHttpServletRequestBuilder request = post("/v1/route/deviation")
                    .with(user("authenticated_user"))
                    .contentType(MediaType.APPLICATION_JSON);

            if (routeDeviationRequestDTO != null) {
                request.content(objectMapper.writeValueAsString(routeDeviationRequestDTO));
            }

            mockMvc.perform(request)
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        public static Stream<Arguments> missingParametersProvider() {
            return Stream.of(
                    Arguments.of(new RouteDeviationRequestDTO(null, -12.432, -12.453)),
                    Arguments.of(new RouteDeviationRequestDTO(UUID.randomUUID(), null, -12.453)),
                    Arguments.of(new RouteDeviationRequestDTO(UUID.randomUUID(), -12.432, null))
            );
        }

        @Test
        void shouldReturnFalseAndDriverCoordsWhenPolylineHasLessThanTwoPoints() throws Exception {
            travel.setPolylineRoute("aaa");
            travelRepository.save(travel);

            when(polylineService.formattedPolylineDecoded(travel.getPolylineRoute()))
                    .thenReturn(List.of(Point.fromLngLat(-38.5, -12.97))); // Retorna lista com apenas 1 ponto

            mockMvc.perform(post("/v1/route/deviation")
                            .with(user("authenticated_user"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(routeDeviationRequestDTO)))
                    .andExpect(status().isOk());

        }

        @Test
        @DisplayName("Polyline inválida que não decodifica → comportamento do formattedPolylineDecoded com string inválida")
        void shouldReturnFalseAndDriverCoordsWhenPolylineDecodingFails() throws Exception {
            travel.setPolylineRoute("invalid_encoded_polyline");
            travelRepository.save(travel);

            RouteDeviationRequestDTO requestDTO = new RouteDeviationRequestDTO(
                    travel.getId(),
                    -12.97000,
                    -38.50000
            );

            when(polylineService.formattedPolylineDecoded("invalid_encoded_polyline"))
                    .thenReturn(List.of());

            mockMvc.perform(post("/v1/route/deviation")
                            .with(user("authenticated_user"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDTO)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isOffRoute").value(false))
                    .andExpect(jsonPath("$.nearestPointLat").exists());
        }
    }*/
}
