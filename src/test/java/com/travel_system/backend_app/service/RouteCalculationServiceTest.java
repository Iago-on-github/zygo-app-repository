package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDeviationDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteCalculationServiceTest {

    @InjectMocks
    private RouteCalculationService routeCalculationService;

    @Mock
    private PolylineService polylineService;

    private final String POLYLINE_MOCK = "encoded_polyline_mock";

    @Nested
    class isRouteDeviation {

        @Test
        @DisplayName("should return offRoute=true when driver is beyond 50m tolerance")
        void shouldReturnOffRouteTrueWhenDriverIsBeyondTolerance() {
            Point p1 = Point.fromLngLat(-38.5016, -12.9714);
            Point p2 = Point.fromLngLat(-38.5016, -12.9800);

            when(polylineService.formattedPolylineDecoded(POLYLINE_MOCK)).thenReturn(List.of(p1, p2));

            RouteDeviationDTO result = routeCalculationService.isRouteDeviation(-12.832, -13.283, POLYLINE_MOCK);

            assertTrue(result.isOffRoute());
            assertTrue(result.distanceToRouteMeters() > 50.0);
        }

        @Test
        @DisplayName("should return offRoute=false when driver is within 50m tolerance")
        void shouldReturnOffRouteFalseWhenDriverIsWithinTolerance() {
            Point p1 = Point.fromLngLat(-38.5016, -12.9714);
            Point p2 = Point.fromLngLat(-38.5016, -12.9800);

            when(polylineService.formattedPolylineDecoded(POLYLINE_MOCK)).thenReturn(List.of(p1, p2));

            RouteDeviationDTO result = routeCalculationService.isRouteDeviation(-12.9750, -38.5016, POLYLINE_MOCK);

            assertFalse(result.isOffRoute());

            assertTrue(result.distanceToRouteMeters() < 50.0);
        }

        @Test
        @DisplayName("should return offRoute=false when polyline has less than two points")
        void shouldReturnOffRouteFalseWhenPolylineHasLessThanTwoPoints() {
            when(polylineService.formattedPolylineDecoded(POLYLINE_MOCK)).thenReturn(List.of(Point.fromLngLat(-12.433, -11.927)));

            RouteDeviationDTO result = routeCalculationService.isRouteDeviation(-12.97, -38.50, POLYLINE_MOCK);

            assertFalse(result.isOffRoute());
            assertEquals(0.0, result.distanceToRouteMeters());
        }

        @Test
        @DisplayName("should return offRoute=true when polylineRoute is null")
        void shouldReturnOffRouteWhenPolylineIsNull() {
            RouteDeviationDTO result = routeCalculationService.isRouteDeviation(-12.97, -38.50, null);

            assertTrue(result.isOffRoute());
        }

        @Test
        @DisplayName("should return offRoute=true when currentLat is null")
        void shouldReturnOffRouteWhenCurrentLatIsNull() {
            RouteDeviationDTO result = routeCalculationService.isRouteDeviation(null, -38.50, POLYLINE_MOCK);

            assertTrue(result.isOffRoute());
            assertEquals(0.0, result.distanceToRouteMeters());
        }

        @Test
        @DisplayName("should return offRoute=true when currentLng is null")
        void shouldReturnOffRouteWhenCurrentLngIsNull() {
            RouteDeviationDTO result = routeCalculationService.isRouteDeviation(-12.97, null, POLYLINE_MOCK);

            assertTrue(result.isOffRoute());
        }
    }
}