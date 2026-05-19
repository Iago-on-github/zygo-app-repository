package com.travel_system.backend_app.config;

import com.travel_system.backend_app.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsSourceConfig corsSourceConfig;

    public SecurityConfig(CorsSourceConfig corsSourceConfig) {
        this.corsSourceConfig = corsSourceConfig;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(TokenConfig tokenConfig) {
        return new JwtAuthenticationFilter(tokenConfig);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers("/v1/auth/**").permitAll()
                                // permite para o servidor externo do rabbitmq
                                .requestMatchers("/api/messaging/auth/**").permitAll()
                                .requestMatchers("/v1/admins/**").hasRole("ADMIN")
                                .requestMatchers("/api/v1/gps/**").hasRole("DRIVER")
                                // temporário para desenvolvimento
                                .anyRequest().permitAll()
                )
                // tratamento de exceptions do spring security
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                (request,
                                 response,
                                 authException) ->
                                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage())))

                .cors(cors ->
                        cors.configurationSource(corsSourceConfig.corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    // temporário para o swagger doc
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers(
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html"
        );
    }
}
