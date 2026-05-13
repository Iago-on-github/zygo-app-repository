package com.travel_system.backend_app.integration.controller;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.repository.UserRepository;
import com.travel_system.backend_app.service.TravelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


public class RabbitMQControllerIT extends IntegrationTestBase {

    @Autowired
    private TokenConfig tokenConfig;
    @Autowired
    private TravelService travelService;
    @Autowired
    private UserRepository userRepository;

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
    }
}
