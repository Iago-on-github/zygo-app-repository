package com.travel_system.backend_app.infrastructure;

import com.travel_system.backend_app.config.constants.RateLimitConstants;
import com.travel_system.backend_app.exceptions.RateLimitExceededException;
import com.travel_system.backend_app.exceptions.RateLimitServiceUnavailableException;
import com.travel_system.backend_app.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class BootstrapRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Value("${security.rate-limit-trust-forwarded-header}")
    private boolean trustForwardedHeader;

    public BootstrapRateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String resolveClientIp = resolveClientIp(request);
        String key = RateLimitConstants.RATE_LIMIT_BOOTSTRAP_KEY + resolveClientIp;

        /*
        * realiza a incrementação das tentativas e verifica se excedeu o limite permitido
        * */

        long currentCount;
        try {
            currentCount = rateLimitService.incrementAndGetCount(key, RateLimitConstants.BOOTSTRAP_RATE_LIMIT_WINDOW);
        } catch (Exception e) {
            throw new RateLimitServiceUnavailableException("Falha ao consultar o serviço de rate limiting", e);
        }

        if (currentCount > RateLimitConstants.MAX_BOOTSTRAP_ATTEMPTS) {
            throw new RateLimitExceededException("Limite de tentativas excedido. Tente novamente mais tarde.");
        }

        return true;
    }

    /*
    * caso a variável de ambiente seja true ele começa a pegar os IPs através do header
    * */
    private String resolveClientIp(HttpServletRequest request) {
        if (trustForwardedHeader) {
            String forwardedFor  = request.getHeader("X-Forwarded-For");

            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
