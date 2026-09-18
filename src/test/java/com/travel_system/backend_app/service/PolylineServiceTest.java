package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.utils.PolylineUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolylineServiceTest {
    /*
     * PADRÕES DOS TESTES UNITÁRIOS
     * 1. TESTAR TODOS AS SAÍDAS (RESULTADOS) DOS MÉTODOS EM QUESTÃO
     * 2. OS MÉTODOS SUCCESS DEVEM CONTER "WithSuccess"
     * 3. OS MÉTODOS FAILURE DEVEM CONTER "ThrowException"
     * 4. MÉTODOS COM VÁRIAS POSSÍVEIS SAÍDAS DEVEM SER OBRIGATORIAMENTE ENGLOBADAS EM CLASSES PRÓPRIAS DE SUCCESS E FAILURE (MESMO DENTRO DA SUA CLASSE DE ORIGEM)
     * 5. NUNCA USAR RUNTIME EX. COMO EXCEÇÃO CORINGA, USE A PRÓPRIA EXCEÇÃO LANÇADA NO MÉTODO
     * 6. SEMPRE ADICIONAR UMA BREVE DESCRIÇÃO COM A ANNOTATION '@DisplayName("...")'.
     * 7. OS TESTES DEVEM OBRIGATORIAMENTE SEGUIR O PADRÃO AAA (ARRANGE, ACT & ASSERT) de forma com que todos os cenários sejam cobertos
     *
     */

    @InjectMocks
    private PolylineService polylineService;

    @Nested
    class formattedPolylineDecoded {

        @Test
        @DisplayName("should return a formatted polyline decoded with success")
        void shouldFormattedPolylineDecodedWithSuccess() {
            // arrange
            String polylineRoute = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

            // act
            List<Point> result = polylineService.formattedPolylineDecoded(polylineRoute);

            // assert
            assertNotNull(result, "result must never be null");
            assertFalse(result.isEmpty());
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("should return silently when polyline route is empty")
        void shouldReturnSilentlyWhenPolylineRouteIsEmpty() {
            // arrange
            String polylineRoute = "";

            // act
            List<Point> result = polylineService.formattedPolylineDecoded(polylineRoute);

            // assert
            assertNull(result);
        }
    }

    @Nested
    class formattedPolylineEncoded {

        @Test
        @DisplayName("should formatted polyline encoded with success")
        void shouldFormattedPolylineEncodedWithSuccess() {
            List<Point> polylineRoute = List.of(
                    Point.fromLngLat(-38.5014, -12.9714),
                    Point.fromLngLat(-38.4500, -12.9000)
            );

            // act
            String result = polylineService.formattedPolylineEncoded(polylineRoute);

            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("should return silently when polyline route is empty")
        void shouldReturnSilentlyWhenPolylineRouteIsEmpty() {
            List<Point> polylineRoute = new ArrayList<>();

            // act
            String result = polylineService.formattedPolylineEncoded(polylineRoute);

            assertNull(result);
        }
    }
}