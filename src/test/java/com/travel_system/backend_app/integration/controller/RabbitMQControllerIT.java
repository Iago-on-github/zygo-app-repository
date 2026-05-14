package com.travel_system.backend_app.integration.controller;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.*;
import com.travel_system.backend_app.service.TravelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.shaded.org.checkerframework.checker.guieffect.qual.UI;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


public class RabbitMQControllerIT extends IntegrationTestBase {

    @MockitoBean
    private TokenConfig tokenConfig;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private TravelService travelService;

    @Autowired
    private TravelRepository travelRepository;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private StudentTravelRepository studentTravelRepository;

    @Value("${rabbitmq_user}")
    private String rabbitmq_user;
    @Value("${rabbitmq_password}")
    private String rabbitmq_password;

    @BeforeEach
    void setUp() {
        // limpa a cada teste
        userRepository.deleteAll();
    }

    @Nested
    class authenticateMessaging {
        @Test
        @DisplayName("using rabbitmq's credentials, should authorized own backend on the system")
        void shouldAuthenticateTheOwnBackendUsingRightCredentials() throws Exception {
            mockMvc.perform(post("/api/messaging/auth/user")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .param("user", rabbitmq_user)
                    .param("password", rabbitmq_password))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string("allow"));
        }

        @Test
        @DisplayName("when token is invalid (password), should NEVER authorize")
        void shouldNeverAuthenticateWhenTokenIsInvalid() throws Exception {
            when(tokenConfig.validateToken("invalid-token")).thenReturn(false);

            mockMvc.perform(post("/api/messaging/auth/user")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("user", rabbitmq_user)
                            .param("password", "invalid-token"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string("deny"));
        }

        @Test
        @DisplayName("should deny access when user is inactive or ID mismatch")
        void shouldNeverAuthenticateWhenUserIsInactiveOrIdMismatch() throws Exception {
            String email = "user@email.com";
            UUID id = UUID.randomUUID();
            String validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJzdHVkZW50QGVtYWlsLmNvbSJ9.dummy";

            when(tokenConfig.validateToken(validToken)).thenReturn(true);
            when(tokenConfig.getSubjectFromToken(validToken)).thenReturn(email);
            when(userRepository.existsByEmailAndIdAndStatus(email, id, GeneralStatus.ACTIVE)).thenReturn(false);

            mockMvc.perform(post("/api/messaging/auth/user")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .param("user", id.toString())
                    .param("password", validToken))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string("deny"));

        }

        @Test
        void shouldAuthenticateWhenTokenIsValidAndUserIsActive() throws Exception {
            String email = "user@email.com";
            UUID id = UUID.randomUUID();
            String validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJzdHVkZW50QGVtYWlsLmNvbSJ9.dummy";

            when(tokenConfig.validateToken(validToken)).thenReturn(true);
            when(tokenConfig.getSubjectFromToken(validToken)).thenReturn(email);
            when(userRepository.existsByEmailAndIdAndStatus(email, id, GeneralStatus.ACTIVE)).thenReturn(true);

            mockMvc.perform(post("/api/messaging/auth/user")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("user", id.toString())
                            .param("password", validToken))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string("allow"));
        }

        @Test
        void shouldNeverAuthenticateWhenErrorOccursDuringTheProcess() throws Exception {
            UUID id = UUID.randomUUID();
            when(tokenConfig.validateToken("invalid_token")).thenThrow(new RuntimeException());

            mockMvc.perform(post("/api/messaging/auth/user")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("user", id.toString())
                            .param("password", "invalid_token"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string("deny"));
        }
    }

    @Nested
    class authenticateVHost {

        @Test
        void shouldAuthenticateVHostWithSuccess() throws Exception {
            String validVHost = "/";

            mockMvc.perform(post("/api/messaging/auth/vhost")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .param("user", rabbitmq_user)
                    .param("vhost", validVHost)
                    .param("ip", "1232"))
                    .andExpect(content().string("allow"))
                    .andExpect(status().isOk());
        }

        @Test
        void shouldNeverAuthenticateVHostWhenIsInvalidVHost() throws Exception {
            String validVHost = "invalid_vhost";

            mockMvc.perform(post("/api/messaging/auth/vhost")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("user", rabbitmq_user)
                            .param("vhost", validVHost)
                            .param("ip", "1232"))
                    .andExpect(content().string("deny"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class authenticateResource {

        @ParameterizedTest
        @MethodSource("permissionProvider")
        void shouldAllowReadOrWriteActionsInPublicExchangesWithSuccess(String permission) throws Exception {
            mockMvc.perform(post("/api/messaging/auth/resource")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("user", rabbitmq_user)
                            .param("vhost", "/")
                            .param("resource", "topic")
                            .param("name", "mocked_name")
                            .param("permission", permission))
                    .andExpect(content().string("allow"))
                    .andExpect(status().isOk());
        }

        public static Stream<Arguments> permissionProvider() {
            return Stream.of(
                    Arguments.of("read"),
                    Arguments.of("write")
            );
        }

        @Test
        @DisplayName("when permission equals 'configure', should ever return false")
        void shouldNeverAllowCreateOrDeleteServerStructures() throws Exception {
            String permission = "configure";

            mockMvc.perform(post("/api/messaging/auth/resource")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("user", rabbitmq_user)
                            .param("vhost", "/")
                            .param("resource", "topic")
                            .param("name", "mocked_name")
                            .param("permission", permission))
                    .andExpect(content().string("deny"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("when 'permission' is diverge 'configure', 'read' or 'write', should not process anything")
        void shouldNeverAllowWhenPermissionIsDivergeToDefaultConfigurations() throws Exception {
            String permission = "diverge_permission";

            mockMvc.perform(post("/api/messaging/auth/resource")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("user", rabbitmq_user)
                            .param("vhost", "/")
                            .param("resource", "topic")
                            .param("name", "mocked_name")
                            .param("permission", permission))
                    .andExpect(content().string("deny"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class authenticateTopic {
        Travel travel;
        Driver driver;
        StudentTravel studentTravel;
        Student student;

        @BeforeEach
        void setUp() {
            driver = new Driver(
                    null, "driver@test.com", "encoded_pass",
                    "João", "Silva", "71999999999",
                    null, GeneralStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(),
                    "Salvador", 0, new ArrayList<>());
            driverRepository.save(driver);

            travel = new Travel(
                    null, null, TravelStatus.TRAVELLING, driver,
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000
            );
            travelRepository.save(travel);

            student = new Student(
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

            studentTravel = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null);
            studentTravelRepository.save(studentTravel);

            travel.setStudentTravels(Set.of(studentTravel));
        }

        @Test
        void shouldAllowDriverAuthenticateWithSuccess() throws Exception {
            String permission = "publish";

            String routingKey = "travel/" + travel.getId();

            when(travelService.isDriverLogged(String.valueOf(driver.getId()), travel.getId())).thenReturn(true);

            mockMvc.perform(post("/api/messaging/auth/topic")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("user", String.valueOf(driver.getId()))
                            .param("routing_key", routingKey)
                            .param("permission", permission))
                    .andExpect(content().string("allow"))
                    .andExpect(status().isOk());
        }

        @Test
        void shouldAllowStudentAuthenticateWithSuccess() throws Exception {
            String permission = "subscribe";

            String routingKey = "travel/" + travel.getId();

            when(travelService.isStudentLogged(student.getId(), travel.getId())).thenReturn(true);

            mockMvc.perform(post("/api/messaging/auth/topic")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("user", String.valueOf(student.getId()))
                            .param("routing_key", routingKey)
                            .param("permission", permission))
                    .andExpect(content().string("allow"))
                    .andExpect(status().isOk());

        }

        @Test
        @DisplayName("should deny and log error when exception occurs during topic auth")
        void shouldNeverAllowWhenErrorOccursDuringAuthProcess() throws Exception {
            // quando algum erro ocorrer durante o processo de verificação, deve cair no cair, não subir erro e retornar apenas deny
            String permission = "subscribe";

            String routingKey = "travel/" + travel.getId();

            when(travelService.isStudentLogged(student.getId(), travel.getId())).thenThrow(new RuntimeException());

            mockMvc.perform(post("/api/messaging/auth/topic")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("user", String.valueOf(student.getId()))
                            .param("routing_key", routingKey)
                            .param("permission", permission))
                    .andExpect(content().string("deny"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should deny when permission is neither publish or subscribe")
        void shouldDenyUnknownPermission() throws Exception {
            String permission = "configure"; // exemplo de permission inválida aqui

            mockMvc.perform(post("/api/messaging/auth/topic")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("user", UUID.randomUUID().toString())
                            .param("routing_key", UUID.randomUUID().toString())
                            .param("permission", permission))
                    .andExpect(content().string("deny"))
                    .andExpect(status().isOk());
        }
    }

}
