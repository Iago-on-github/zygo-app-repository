package com.travel_system.backend_app.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.config.FirebaseConfig;
import com.travel_system.backend_app.model.dtos.mapboxApi.MapboxApiResponse;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.service.*;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import okhttp3.Route;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected RabbitTemplate rabbitTemplate; // evita conexão real com o rabbitmq

    @MockitoBean
    protected MapboxAPIService mapboxAPIService; // evita chamada externa da api

    @MockitoBean
    protected FirebaseConfig firebaseConfig; // evita conexão real com o firebase

    @MockitoBean
    protected PolylineService polylineService;

    @MockitoBean
    protected FirebaseNotificationSender firebaseNotificationSender;

    @MockitoBean
    protected RouteCalculationService routeCalculationService;

    @MockitoSpyBean
    protected TravelService travelService;

    @MockitoSpyBean
    protected PushNotificationService pushNotificationService;


    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:15")
                    .asCompatibleSubstituteFor("postgres");

    private static final DockerImageName REDIS_IMAGE =
            DockerImageName.parse("redis:7");

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("zygo_database_test")
                    .withUsername("zygo_test")
                    .withPassword("zygo_test");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
