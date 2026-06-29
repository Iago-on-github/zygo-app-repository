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
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatchers;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsSourceConfig corsSourceConfig;

    public SecurityConfig(CorsSourceConfig corsSourceConfig) {
        this.corsSourceConfig = corsSourceConfig;
    }

    final String ROLE_ADMIN = "ADMIN";
    final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    final String ROLE_DRIVER = "DRIVER";
    final String ROLE_USER = "USER";

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(TokenConfig tokenConfig) {
        return new JwtAuthenticationFilter(tokenConfig);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    configureTravelEndpoints(auth);
                    configureTravelTrackingEndpoints(auth);
                    configurePermitAllEndpoints(auth);
                    configureAdminsEndpoints(auth);
                    configureDriverEndpoints(auth);
                    configureStudentEndpoints(auth);
                    configureGpsEndpoints(auth);
                    configureCustomersEndpoints(auth);
                    configureAnyRequireAuthEndpoints(auth);
                })
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

    private void configureTravelEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                .requestMatchers("/v1/travel/create").hasRole(ROLE_DRIVER)
                .requestMatchers("/v1/travel/{travelId}/start").hasRole(ROLE_DRIVER)
                .requestMatchers("/v1/travel/{travelId}/end").hasRole(ROLE_DRIVER)

                .requestMatchers("/v1/travel/{travelId}/join").hasRole(ROLE_USER)
                .requestMatchers("/v1/travel/{travelId}/leave").hasRole(ROLE_USER)

                .requestMatchers("/v1/travel/{travelId}/preview").hasRole(ROLE_DRIVER);
    }

    private void configureTravelTrackingEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/tracking/**").hasRole(ROLE_DRIVER);
    }

    private void configurePermitAllEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                .requestMatchers("/v1/auth/**").permitAll() // endpoints de login
                .requestMatchers("/v1/messaging/auth/**").permitAll() // servidor externo do rabbitmq
                .requestMatchers("/testing/**").permitAll(); // testes
//                .requestMatchers("/v1/current/**").permitAll(); // testes
    }

    private void configureAdminsEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/admins/**").hasAnyRole(ROLE_ADMIN, ROLE_PLATFORM_ADMIN);
    }

    private void configureDriverEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/drivers/**").hasAnyRole(ROLE_ADMIN, ROLE_PLATFORM_ADMIN);
    }

    private void configureStudentEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/students/me").hasAnyRole(ROLE_USER, ROLE_ADMIN, ROLE_PLATFORM_ADMIN);
        auth.requestMatchers("/v1/students/**").hasAnyRole(ROLE_ADMIN, ROLE_PLATFORM_ADMIN);
    }

    private void configureGpsEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/gps/**").hasAnyRole(ROLE_DRIVER, ROLE_PLATFORM_ADMIN);
    }

    private void configureCustomersEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/customers/**").hasAnyRole(ROLE_PLATFORM_ADMIN);
    }

    private void configureAnyRequireAuthEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.anyRequest().authenticated();
    }
}
