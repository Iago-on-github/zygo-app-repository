package com.travel_system.backend_app.config;

import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import com.travel_system.backend_app.security.JwtAuthenticationFilter;
import com.travel_system.backend_app.service.CurrentUserService;
import com.travel_system.backend_app.service.UserProfileResolverService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.compress.archivers.dump.DumpArchiveEntry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
    public JwtAuthenticationFilter jwtAuthenticationFilter(TokenConfig tokenConfig, CurrentUserService currentUserService, UserProfileResolverService userProfileResolverService, CustomerRepository customerRepository, UserAccountRepository userAccountRepository) {
        return new JwtAuthenticationFilter(tokenConfig, currentUserService, userProfileResolverService, customerRepository, userAccountRepository);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    configurePermitAllEndpoints(auth);
                    configureDriverEndpoints(auth);
                    configureStudentEndpoints(auth);
                    configureTravelEndpoints(auth);
                    configureTravelTrackingEndpoints(auth);
                    configureStudentRouteStopEndpoints(auth);
                    configureRouteStopAssignmentEndpoints(auth);
                    configureRouteStopEndpoints(auth);
                    configureStandardRouteEndpoints(auth);
                    configureCustomersEndpoints(auth);
                    configureAdminsEndpoints(auth);
                    configurePlatformAdministratorEndpoints(auth);
                    configureSetupAuthenticationEndpoints(auth);
                    configureSensitiveOperationEndpoints(auth);
                    configureCitiesEndpoints(auth);
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
                .requestMatchers("/v1/travel/{travelId}/join").hasRole(ROLE_USER)
                .requestMatchers("/v1/travel/{travelId}/leave").hasRole(ROLE_USER)

                .requestMatchers("/v1/travel/**").hasRole(ROLE_DRIVER);
    }

    private void configureTravelTrackingEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/tracking/{travelId}/standard").hasAnyRole(ROLE_USER, ROLE_DRIVER);
        auth.requestMatchers("/v1/tracking/{travelId}/route-stops").hasAnyRole(ROLE_USER, ROLE_DRIVER);

        auth.requestMatchers("/v1/tracking/**").hasRole(ROLE_DRIVER);
    }

    private void configurePermitAllEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                .requestMatchers("/v1/auth/**").permitAll() // endpoints de login
                .requestMatchers("/v1/messaging/auth/**").permitAll() // servidor externo do rabbitmq
                .requestMatchers("/api/private-test/**").permitAll() // testes
                .requestMatchers("/actuator/**").permitAll();
    }

    private void configureAdminsEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/admins/**").hasAnyRole(ROLE_ADMIN, ROLE_PLATFORM_ADMIN);
//        auth.requestMatchers("/v1/admins/**").permitAll();
    }

    private void configureDriverEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers(HttpMethod.POST, "/v1/drivers").permitAll();

        auth.requestMatchers("/v1/drivers/me").hasAnyRole(ROLE_DRIVER);
        auth.requestMatchers("/v1/drivers/**").hasAnyRole(ROLE_ADMIN, ROLE_PLATFORM_ADMIN);
    }

    private void configureStudentEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers(HttpMethod.POST, "/v1/students/").permitAll();

        auth.requestMatchers("/v1/students/me").hasAnyRole(ROLE_USER, ROLE_ADMIN, ROLE_PLATFORM_ADMIN);
        auth.requestMatchers("/v1/students/**").hasAnyRole(ROLE_ADMIN, ROLE_PLATFORM_ADMIN);
    }

    private void configureCustomersEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/customers/**").hasAnyRole(ROLE_PLATFORM_ADMIN);
//        auth.requestMatchers("/v1/customers/**").permitAll();
    }

    private void configureStandardRouteEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/standard-route/**").hasAnyRole(ROLE_ADMIN, ROLE_PLATFORM_ADMIN);
    }

    private void configureRouteStopEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/route-stops/{customerId}/customer").hasRole(ROLE_PLATFORM_ADMIN);

        auth.requestMatchers("/v1/route-stops/**").hasAnyRole(ROLE_ADMIN, ROLE_PLATFORM_ADMIN);
    }

    private void configureRouteStopAssignmentEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/route-assignment/**").hasAnyRole(ROLE_ADMIN, ROLE_PLATFORM_ADMIN);
    }

    private void configureStudentRouteStopEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/route-assignment/{routeStopId}/associate/{standardRouteId}").hasAnyRole(ROLE_ADMIN, ROLE_PLATFORM_ADMIN, ROLE_USER);
        auth.requestMatchers("/v1/route-assignment/{studentId}/update/{standardRouteId}").hasAnyRole(ROLE_ADMIN, ROLE_PLATFORM_ADMIN, ROLE_USER);
        auth.requestMatchers("/v1/route-assignment/{routeStopId}/remove/{standardRouteId}").hasAnyRole(ROLE_ADMIN, ROLE_PLATFORM_ADMIN, ROLE_USER);

        auth.requestMatchers("/v1/route-stop-students/**").hasAnyRole(ROLE_ADMIN, ROLE_PLATFORM_ADMIN, ROLE_USER, ROLE_DRIVER);
    }

    private void configurePlatformAdministratorEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                .requestMatchers("/new").hasRole(ROLE_PLATFORM_ADMIN)
                .requestMatchers("/v1/internal/platform-admin/bootstrap").permitAll();

    }

    private void configureSensitiveOperationEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/security/sensitive-operations/**").permitAll();

    }

    private void configureSetupAuthenticationEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/auth/set-up").hasRole(ROLE_PLATFORM_ADMIN);

    }

    private void configureCitiesEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/v1/cities/**").hasRole(ROLE_PLATFORM_ADMIN);

    }

    private void configureAnyRequireAuthEndpoints(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.anyRequest().authenticated();
    }
}
