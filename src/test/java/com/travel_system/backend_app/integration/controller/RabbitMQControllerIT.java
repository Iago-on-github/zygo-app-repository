package com.travel_system.backend_app.integration.controller;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.*;
import com.travel_system.backend_app.service.TravelService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
    private UserAccountRepository userAccountRepository;

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
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private CityRepository cityRepository;

    @Value("${rabbitmq_user}")
    private String rabbitmq_user;
    @Value("${rabbitmq_password}")
    private String rabbitmq_password;

    @AfterEach
    void tearDown() {
        //limpa os repos a cada teste realiazado

        studentTravelRepository.deleteAllInBatch();
        travelRepository.deleteAllInBatch();
        studentRepository.deleteAllInBatch();
        driverRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        cityRepository.deleteAllInBatch();
    }

/*    @Nested
    class authenticateMessaging {
        @Test
        @DisplayName("using rabbitmq's credentials, should authorized own backend on the system")
        void shouldAuthenticateTheOwnBackendUsingRightCredentials() throws Exception {
            mockMvc.perform(post("/v1/messaging/auth/user")
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

            mockMvc.perform(post("/v1/messaging/auth/user")
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
            when(userAccountRepository.existsByEmailAndIdAndStatus(email, id, GeneralStatus.ACTIVE)).thenReturn(false);

            mockMvc.perform(post("/v1/messaging/auth/user")
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
            when(userAccountRepository.existsByEmailAndIdAndStatus(email, id, GeneralStatus.ACTIVE)).thenReturn(true);

            mockMvc.perform(post("/v1/messaging/auth/user")
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

            mockMvc.perform(post("/v1/messaging/auth/user")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("user", id.toString())
                            .param("password", "invalid_token"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string("deny"));
        }
    }*/

    @Nested
    class authenticateVHost {

        @Test
        void shouldAuthenticateVHostWithSuccess() throws Exception {
            String validVHost = "/";

            mockMvc.perform(post("/v1/messaging/auth/vhost")
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

            mockMvc.perform(post("/v1/messaging/auth/vhost")
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
            mockMvc.perform(post("/v1/messaging/auth/resource")
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

            mockMvc.perform(post("/v1/messaging/auth/resource")
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

            mockMvc.perform(post("/v1/messaging/auth/resource")
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

/*    @Nested
    class authenticateTopic {
        City city;
        Customer customer;
        Travel travel;
        Driver driver;
        StudentTravel studentTravel;
        Student student;

        @BeforeEach
        void setUp() {
            city = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(city);

            customer = new Customer(null, "Universidade Cruz", "universidade-cruz", "32.345.678/0001-90", true, city, ClientSector.PUBLIC_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(customer);

            driver = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 12);
            driverRepository.save(driver);

            travel = new Travel(null, TravelStatus.PENDING, driver, Instant.parse("2026-07-16T10:00:00Z"), null, TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer, null);
            travel = travelRepository.saveAndFlush(travel);

            student = new Student(null, "email@exemplo.com", "senha123", "studentName", "studentLastName", "11999999999", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia de Software");
            studentRepository.save(student);

            studentTravel = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
            studentTravelRepository.save(studentTravel);

            travel.setStudentTravels(Set.of(studentTravel));
            travelRepository.saveAndFlush(travel);
        }

        @Test
        void shouldAllowDriverAuthenticateWithSuccess() throws Exception {
            String permission = "publish";

            String routingKey = "travel/" + travel.getId();

            when(travelService.isDriverLogged(String.valueOf(driver.getId()), travel.getId())).thenReturn(true);

            mockMvc.perform(post("/v1/messaging/auth/topic")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("authenticatedUserId", String.valueOf(driver.getId()))
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

            mockMvc.perform(post("/v1/messaging/auth/topic")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("authenticatedUserId", String.valueOf(student.getId()))
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

            mockMvc.perform(post("/v1/messaging/auth/topic")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("authenticatedUserId", String.valueOf(student.getId()))
                            .param("routing_key", routingKey)
                            .param("permission", permission))
                    .andExpect(content().string("deny"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should deny when permission is neither publish or subscribe")
        void shouldDenyUnknownPermission() throws Exception {
            String permission = "configure"; // exemplo de permission inválida aqui

            mockMvc.perform(post("/v1/messaging/auth/topic")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .param("authenticatedUserId", UUID.randomUUID().toString())
                            .param("routing_key", UUID.randomUUID().toString())
                            .param("permission", permission))
                    .andExpect(content().string("deny"))
                    .andExpect(status().isOk());
        }
    }*/

}
