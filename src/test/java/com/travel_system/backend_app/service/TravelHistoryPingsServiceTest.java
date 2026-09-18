package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.TravelLocationHistory;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.repository.TravelLocationHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TravelHistoryPingsServiceTest {

    @InjectMocks
    private TravelHistoryPingsService service;

    @Mock
    private TravelLocationHistoryRepository travelLocationHistoryRepository;

    @Nested
    class saveTravelLocationHistoryData {

        @Test
        @DisplayName("should save travel location history data with success")
        void shouldSaveTravelLocationHistoryDataWithSuccess() {
            UUID travelId = UUID.randomUUID();
            UUID cityId = UUID.randomUUID();

            TravelLocationHistory travelLocationHistory = new TravelLocationHistory(travelId, cityId, -12.345678, -38.987654, Instant.now());

            VehicleLocationRequestDTO vehicleLocationRequestDTO = new VehicleLocationRequestDTO(travelId, -12.345678, -38.987654, 65.5, 180.0);

            when(travelLocationHistoryRepository.save(any(TravelLocationHistory.class))).thenReturn(travelLocationHistory);

            service.saveTravelLocationHistoryData(cityId.toString(), travelId.toString(), Instant.now(), vehicleLocationRequestDTO);

            ArgumentCaptor<TravelLocationHistory> TravelLocHistCaptor = ArgumentCaptor.forClass(TravelLocationHistory.class);

            verify(travelLocationHistoryRepository, times(1)).save(TravelLocHistCaptor.capture());
            TravelLocationHistory storedValue = TravelLocHistCaptor.getValue();

            assertEquals(travelLocationHistory.getTravelId(), storedValue.getTravelId());
            assertEquals(travelLocationHistory.getCityId(), storedValue.getCityId());
            assertEquals(travelLocationHistory.getLatitude(), storedValue.getLatitude());
            assertEquals(travelLocationHistory.getLongitude(), storedValue.getLongitude());

            assertNotNull(storedValue.getTimestamp());
        }

        @ParameterizedTest
        @DisplayName("should return silently when cityId or travelId is null")
        @MethodSource("nullFieldsProvider")
        void shouldReturnSilentlyWhenCityIdOrTravelIdIsNull(String travelId, String cityId) {
            service.saveTravelLocationHistoryData(cityId, travelId, null, null);

            verify(travelLocationHistoryRepository, never()).save(any());
        }

        public static Stream<Arguments> nullFieldsProvider() {
            return Stream.of(
                    Arguments.of(null, "valid_uuid"),
                    Arguments.of("valid_uuid", null),
                    Arguments.of(null, null)
            );
        }
    }
}