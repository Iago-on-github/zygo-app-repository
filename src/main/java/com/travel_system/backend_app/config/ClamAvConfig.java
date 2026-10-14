package com.travel_system.backend_app.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.capybara.clamav.ClamavClient;
import xyz.capybara.clamav.Platform;

@Configuration
@EnableConfigurationProperties(ClamAvProperties.class)
public class ClamAvConfig {

    // cliente do clamd via TCP (INSTREAM) não abre conexão na criação, apenas a cada scan
    @Bean
    public ClamavClient clamavClient(ClamAvProperties properties) {
        return new ClamavClient(properties.getHost(), properties.getPort(), Platform.UNIX);
    }

}
