package com.travel_system.backend_app.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    @Value("${security.rate-limit-trust-forwarded-header}")
    private boolean trustForwardedHeader;

    // só confia no X-Forwarded-For quando o proxy (Nginx) sobrescreve o cabeçalho em produção
    public String resolve(HttpServletRequest request) {
        if (trustForwardedHeader) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
