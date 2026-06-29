package com.travel_system.backend_app.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.travel_system.backend_app.exceptions.InvalidJwtAuthenticationToken;
import com.travel_system.backend_app.model.dtos.response.LoginResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RefreshTokenResponseDTO;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class TokenConfig {

    @Value("${jwt_secret}")
    private String secret;

    @Value("${jwt.token.expires-length}")
    private Integer validityInMilliseconds;

    Algorithm algorithm = null;

    private final UserDetailsService userDetailsService;

    public TokenConfig(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @PostConstruct
    protected void init() {
        secret = Base64.getEncoder().encodeToString(secret.getBytes());
        algorithm = Algorithm.HMAC256(secret.getBytes());
    }

    public LoginResponseDTO createAccessToken(String email, List<String> roles, UUID customerId)  {
        Instant now = Instant.now();
        Instant validity = now.plus(validityInMilliseconds, ChronoUnit.MILLIS);

        var accessToken = getAccessToken(email, roles, now, validity, customerId);
        var refreshToken = getRefreshToken(email, roles, now, customerId);

        return new LoginResponseDTO(email, true, now, validity, accessToken, refreshToken);
    }

    private String getAccessToken(String email, List<String> roles, Instant now, Instant validity, UUID customerId) {
        String issuerUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();

        JWTCreator.Builder builder = JWT.create()
                .withClaim("roles", roles)
                .withIssuedAt(now)
                .withExpiresAt(validity)
                .withSubject(email)
                .withIssuer(issuerUri);

        if (!isPlatformAdmin(roles)) {
            builder.withClaim("customerId", customerId.toString());
        }

        return builder.sign(algorithm).strip();
    }

    private String getRefreshToken(String email, List<String> roles, Instant now, UUID customerId) {
        Instant validityRefreshToken = now.plus(validityInMilliseconds * 3, ChronoUnit.MILLIS);

        JWTCreator.Builder builder = JWT.create()
                .withClaim("roles", roles)
                .withIssuedAt(now)
                .withExpiresAt(validityRefreshToken)
                .withSubject(email);

        if (!isPlatformAdmin(roles)) {
            builder.withClaim("customerId", customerId.toString());
        }

        return builder.sign(algorithm).strip();
    }

    public RefreshTokenResponseDTO refreshToken(String refreshToken) {
        if (refreshToken.contains("Bearer ")) refreshToken = refreshToken.substring("Bearer ".length());

        JWTVerifier jwtVerifier = JWT.require(algorithm).build();
        DecodedJWT decodedJWT = jwtVerifier.verify(refreshToken);

        String email = decodedJWT.getSubject();
        List<String> roles = decodedJWT.getClaim("roles").asList(String.class);

        UUID customerId = null;

        if (!isPlatformAdmin(roles)) {
            String strCustomerId = decodedJWT.getClaim("customerId").asString();
            customerId = UUID.fromString(strCustomerId);
        }

        // chama o metodo e retorna somente os campos necessários
        LoginResponseDTO token = createAccessToken(email, roles, customerId);

        return new RefreshTokenResponseDTO(token.accessToken(), token.refreshToken(), token.expiration());
    }

    // extrai infos de autenticacao do token
    public Authentication getAuthentication(String token) {
        DecodedJWT decodedJWT = decodedToken(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(decodedJWT.getSubject());
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    // valida e decodifica o token jwt
    private DecodedJWT decodedToken(String token) {
        JWTVerifier verifier = JWT.require(algorithm).build();
        return verifier.verify(token);
    }

    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) return bearerToken.substring("Bearer ".length());

        else return null;
    }

    public String getSubjectFromToken(String token) {
        DecodedJWT decodedJWT = decodedToken(token);

        return decodedJWT.getSubject();
    }

    public UUID getCustomerIdFromToken(String token) {
        DecodedJWT decodedJWT = decodedToken(token);

        return UUID.fromString(decodedJWT.getClaim("customerId").asString());
    }

    public Boolean validateToken(String token) {
        DecodedJWT decodedJWT = decodedToken(token);
        try {
            return !decodedJWT.getExpiresAt().before(new Date());
        } catch(Exception e) {
            throw new InvalidJwtAuthenticationToken("Token expirado ou inválido");
        }
    }

    public boolean isPlatformAdmin(List<String> roles) {
        return roles.stream().anyMatch(r -> r.equals("ROLE_PLATFORM_ADMIN"));
    }

    public List<String> getRolesFromToken(String token) {
        DecodedJWT decodedJWT = decodedToken(token);

        return decodedJWT.getClaim("roles").asList(String.class);
    }
}
