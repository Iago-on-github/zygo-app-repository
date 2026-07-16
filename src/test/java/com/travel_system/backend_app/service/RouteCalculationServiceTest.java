package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDeviationDTO;
import com.travel_system.backend_app.model.dtos.request.RouteDeviationRequestDTO;
import com.travel_system.backend_app.model.enums.CitySize;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.TravelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteCalculationServiceTest {

    @InjectMocks
    private RouteCalculationService routeCalculationService;

    @Mock
    private TravelRepository travelRepository;

    @Mock
    private PolylineService polylineService;

    private final String POLYLINE_MOCK = "encoded_polyline_mock";

    @Nested
    class isRouteDeviation {
        Travel travel;

        @BeforeEach
        void setUp() {
            travel = new Travel(UUID.randomUUID(), TravelStatus.TRAVELLING, null, Instant.now().minusSeconds(1800), Instant.now().minusSeconds(900), TravelPeriod.AFTERNOON, null, "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
                    2400.0,
                    25000.0,
                    -12.9714,
                    -38.5016,
                    -12.8000,
                    -38.4000,
                    "Feira de Santana",
                    new Customer()
            );
            travel.setPolylineRoute(POLYLINE_MOCK);
        }

        @Test
        @DisplayName("should return offRoute=true when driver is beyond 50m tolerance")
        void shouldReturnOffRouteTrueWhenDriverIsBeyondTolerance() {
            Point p1 = Point.fromLngLat(-38.5016, -12.9714);
            Point p2 = Point.fromLngLat(-38.5016, -12.9800);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            when(polylineService.formattedPolylineDecoded(POLYLINE_MOCK)).thenReturn(List.of(p1, p2));

            RouteDeviationDTO result = routeCalculationService
                    .isRouteDeviation(new RouteDeviationRequestDTO(travel.getId(), -12.832, -13.283));

            assertTrue(result.isOffRoute());
            assertTrue(result.distanceToRouteMeters() > 50.0);
        }

        @Test
        @DisplayName("should return offRoute=false when driver is within 50m tolerance")
        void shouldReturnOffRouteFalseWhenDriverIsWithinTolerance() {
            Point p1 = Point.fromLngLat(-38.5016, -12.9714);
            Point p2 = Point.fromLngLat(-38.5016, -12.9800);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            when(polylineService.formattedPolylineDecoded(POLYLINE_MOCK))
                    .thenReturn(List.of(p1, p2));

            RouteDeviationDTO result = routeCalculationService
                    .isRouteDeviation(new RouteDeviationRequestDTO(travel.getId(), -12.9750, -38.5016));

            System.out.println("Distância calc debugging: " + result.distanceToRouteMeters());

            assertFalse(result.isOffRoute());
            assertTrue(result.distanceToRouteMeters() < 50.0);
        }

        @Test
        @DisplayName("should return offRoute=false when polyline has less than two points")
        void shouldReturnOffRouteFalseWhenPolylineHasLessThanTwoPoints() {
            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            when(polylineService.formattedPolylineDecoded(POLYLINE_MOCK)).thenReturn(List.of(Point.fromLngLat(-12.433, -11.927)));

            RouteDeviationDTO result = routeCalculationService
                    .isRouteDeviation(new RouteDeviationRequestDTO(travel.getId(), -12.97, -38.50));

            assertFalse(result.isOffRoute());
            assertEquals(0.0, result.distanceToRouteMeters());
        }

        @Test
        @DisplayName("should return offRoute = false when polylineRoute is null")
        void shouldReturnOffRouteFalseWhenPolylineIsNull() {
            travel.setPolylineRoute(null);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            RouteDeviationDTO result = routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(travel.getId(), -12.97, -38.50));

            assertFalse(result.isOffRoute());
        }

        @Test
        @DisplayName("should return offRoute = false when currentLat is null")
        void shouldReturnFalseFromOffRouteWhenCurrentLatIsNull() {
            RouteDeviationDTO result = routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(travel.getId(), null, -38.50));

            assertFalse(result.isOffRoute());
            assertEquals(0.0, result.distanceToRouteMeters());
        }

        @Test
        @DisplayName("should return offRoute = false when currentLng is null")
        void shouldReturnFalseFromOffRouteWhenCurrentLngIsNull() {
            RouteDeviationDTO result = routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(travel.getId(), -12.97, null));

            assertFalse(result.isOffRoute());
        }
    }

    @Nested
    class calculateHaversineDistanceInMeters {

        @Test
        @DisplayName("should return 0.0 when coordinates are the same")
        void shouldReturnZeroWhenCoordinatesAreTheSame() {
            Double result = routeCalculationService.calculateHaversineDistanceInMeters(
                    -12.9714, -38.5016,
                    -12.9714, -38.5016);

            assertEquals(0.0, result, 0.001);
        }

        @Test
        @DisplayName("should be symmetric - distance A to B equals B to A")
        void shouldBeSymmetric() {
            Double ab = routeCalculationService.calculateHaversineDistanceInMeters(
                    -12.9714, -38.5016,
                    -12.9800, -38.5100);

            Double ba = routeCalculationService.calculateHaversineDistanceInMeters(
                    -12.9800, -38.5100,
                    -12.9714, -38.5016);

            assertEquals(ab, ba, 0.001);
        }

        @Test
        @DisplayName("should return approximately 111195m for 1 degree of latitude difference at equator")
        void shouldReturnApproximatelyOneDegreeLatitudeDistanceAtEquator() {
            Double result = routeCalculationService.calculateHaversineDistanceInMeters(
                    0.0, 0.0,
                    1.0, 0.0);

            // 1 grau de latitude no equador, aprox. 111.195 km
            assertEquals(111195.0, result, 100.0);
        }

        @Test
        @DisplayName("should return approximately 20015km for antipodal points")
        void shouldReturnApproximatelyHalfEarthCircumferenceForAntipodalPoints() {
            Double result = routeCalculationService.calculateHaversineDistanceInMeters(
                    0.0, 0.0,
                    0.0, 180.0);

            // metade da circunferência da terra, aprox. 20.015 km
            assertEquals(20015000.0, result, 1000.0);
        }

        @Test
        @DisplayName("should return straight-line distance between two known points in Salvador BA")
        void shouldReturnCorrectDistanceBetweenTwoKnownPointsInSalvador() {
            // reitoria UFBA → Faculdade de Medicina UFBA
            // distância em linha reta (Haversine) ≈ 852m
            // obs: Google Maps vai retornar aprox. 1270m pois segue o traçado das ruas
            Double result = routeCalculationService.calculateHaversineDistanceInMeters(
                    -13.0010, -38.5078,
                    -12.9940, -38.5110);

            assertEquals(852.0, result, 50.0);
        }
    }
}