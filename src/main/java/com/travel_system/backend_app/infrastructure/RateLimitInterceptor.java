package com.travel_system.backend_app.infrastructure;

import com.travel_system.backend_app.annotations.RateLimited;
import com.travel_system.backend_app.exceptions.RateLimitExceededException;
import com.travel_system.backend_app.model.dtos.security.RateLimitResult;
import com.travel_system.backend_app.model.enums.KeyType;
import com.travel_system.backend_app.model.enums.RateLimitPolicy;
import com.travel_system.backend_app.service.HmacTokenService;
import com.travel_system.backend_app.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;

    public RateLimitInterceptor(RateLimitService rateLimitService, ClientIpResolver clientIpResolver) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimited rateLimited = resolveAnnotation(handlerMethod);
        if (rateLimited == null) {
            return true;
        }

        RateLimitPolicy policy  = rateLimited.value();
        RateLimitResult result = rateLimitService.consume(policy, resolveIdentifier(policy, request));

        if (!result.allowed()) {
            throw new RateLimitExceededException("Limite de tentativas excedido. Tente novamente mais tarde.", result.retryAfterSeconds());
        }

        return true;
    }

    // anotação do método tem prioridade sobre a da classe
    private RateLimited resolveAnnotation(HandlerMethod handlerMethod) {
        RateLimited methodAnnotation = handlerMethod.getMethodAnnotation(RateLimited.class);

        return methodAnnotation != null ? methodAnnotation : AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RateLimited.class);
    }

    private String resolveIdentifier(RateLimitPolicy policy, HttpServletRequest request) {
        if (policy.keyType() == KeyType.USER) {
            String user = resolveAuthenticatedUser();
            if (user != null) {
                // hash para não gravar o e-mail em texto no Redis
                return "u:" + HmacTokenService.calculateSimpleHash(user);
            }
            // política de usuário em endpoint sem autenticação: limita por IP em vez de liberar
        }
        return "ip:" + clientIpResolver.resolve(request);

    }

    private String resolveAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}
