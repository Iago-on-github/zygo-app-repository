package com.travel_system.backend_app.integration.controller;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.integration.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class RouteCalculationControllerIT extends IntegrationTestBase {

    @Nested
    class isRouteDeviation {

        @Test
        @DisplayName("Should return false when driver is within 50 meters route tolerance")
        void shouldReturnFalseWhenDriverIsWithinRouteTolerance() throws Exception {
            // Rota simplificada: dois pontos próximos (≈ 100 metros de distância entre si)
            Point routeStart = Point.fromLngLat(-38.50000, -12.97000);
            Point routeEnd = Point.fromLngLat(-38.49900, -12.96900);
            List<Point> mockedRoute = List.of(routeStart, routeEnd);

            String currentLat = "-12.97000";
            String currentLong = "-38.50000";
            String polylineRoute = "mocked_encoded_polyline";

            when(polylineService.formattedPolylineDecoded(polylineRoute)).thenReturn(mockedRoute);

            mockMvc.perform(get("/routeCalculation/deviation")
                    .param("currentLat", currentLat)
                    .param("currentLong", currentLong)
                    .param("polylineRoute", polylineRoute))
                    .andDo(print())
                    .andExpect(status().isOk())

                    .andExpect(jsonPath("$.distanceToRouteMeters").exists())
                    .andExpect(jsonPath("$.distanceToRouteMeters").value(0.0))
                    .andExpect(jsonPath("$.isOffRoute").value(false))
                    .andExpect(jsonPath("$.nearestPointLat").value(-12.97000))
                    .andExpect(jsonPath("$.nearestPointLng").value(-38.50000));
        }

        @Test
        @DisplayName("Should return true when driver exceeds 50 meters route tolerance")
        void shouldReturnTrueWhenDriverIsOutsideRouteTolerance() throws Exception {
            String polylineRoute = "route_test_offset";

            Point routeStart = Point.fromLngLat(-38.50000, -12.97000);
            Point routeEnd   = Point.fromLngLat(-38.50000, -12.96000);

            when(polylineService.formattedPolylineDecoded(polylineRoute))
                    .thenReturn(List.of(routeStart, routeEnd));

            String driverLat = "-12.96500";
            String driverLng = "-38.49900";

            mockMvc.perform(get("/routeCalculation/deviation")
                            .param("currentLat", driverLat)
                            .param("currentLong", driverLng)
                            .param("polylineRoute", polylineRoute))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.isOffRoute").value(true))
                    .andExpect(jsonPath("$.distanceToRouteMeters").value(greaterThan(50.0)))
                    .andExpect(jsonPath("$.nearestPointLat").value(-12.96500))
                    .andExpect(jsonPath("$.nearestPointLng").value(-38.50000));

        }

        @ParameterizedTest
        @MethodSource("missingParametersProvider")
        void shouldReturnOffRouteTrueWhenRequiredParameterIsMissing(String currentLat, String currentLng, String polylineRoute) throws Exception {

            MockHttpServletRequestBuilder request = get("/routeCalculation/deviation");

            if (currentLat != null) request = request.param("currentLat", currentLat);
            if (currentLng != null) request = request.param("currentLong", currentLng);
            if (polylineRoute != null) request = request.param("polylineRoute", polylineRoute);

            mockMvc.perform(request)
                    .andExpect(status().isBadRequest());
        }

        public static Stream<Arguments> missingParametersProvider() {
            return Stream.of(
                    Arguments.of(null, "-12.432", "polyline_mocked"),
                    Arguments.of("-11.432", null, "polyline_mocked"),
                    Arguments.of("-11.432", "-12.432", null)
            );
        }

        @Test
        void shouldReturnFalseAndDriverCoordsWhenPolylineHasLessThanTwoPoints() throws Exception {
            String currentLat = "-12.97000";
            String currentLong = "-38.50000";
            String polylineRoute = "short_polyline";

            when(polylineService.formattedPolylineDecoded(polylineRoute))
                    .thenReturn(List.of(Point.fromLngLat(-38.5, -12.97))); // Retorna lista com apenas 1 ponto

            mockMvc.perform(get("/routeCalculation/deviation")
                            .param("currentLat", currentLat)
                            .param("currentLong", currentLong)
                            .param("polylineRoute", polylineRoute))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.isOffRoute").value(false))
                    .andExpect(jsonPath("$.distanceToRouteMeters").value(0.0))
                    .andExpect(jsonPath("$.nearestPointLat").value(Double.parseDouble(currentLat)))
                    .andExpect(jsonPath("$.nearestPointLng").value(Double.parseDouble(currentLong)));
        }

        @Test
        @DisplayName("Polyline inválida que não decodifica → comportamento do formattedPolylineDecoded com string inválida")
        void shouldReturnFalseAndDriverCoordsWhenPolylineDecodingFails() throws Exception {
            String currentLat = "-12.97000";
            String currentLong = "-38.50000";
            String invalidPolyline = "invalid_encoded_polyline";

            when(polylineService.formattedPolylineDecoded(invalidPolyline))
                    .thenReturn(List.of());

            mockMvc.perform(get("/routeCalculation/deviation")
                            .param("currentLat", currentLat)
                            .param("currentLong", currentLong)
                            .param("polylineRoute", invalidPolyline))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.isOffRoute").value(false))
                    .andExpect(jsonPath("$.distanceToRouteMeters").value(0.0))
                    .andExpect(jsonPath("$.nearestPointLat").value(Double.parseDouble(currentLat)))
                    .andExpect(jsonPath("$.nearestPointLng").value(Double.parseDouble(currentLong)));
        }
    }
}
