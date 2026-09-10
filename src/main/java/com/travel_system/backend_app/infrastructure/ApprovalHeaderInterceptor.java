package com.travel_system.backend_app.infrastructure;

import com.travel_system.backend_app.exceptions.NotAuthorizedException;
import com.travel_system.backend_app.service.ApprovalAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApprovalHeaderInterceptor implements HandlerInterceptor {

    private final ApprovalAuthorizationService approvalAuthorizationService;

    public ApprovalHeaderInterceptor(ApprovalAuthorizationService approvalAuthorizationService) {
        this.approvalAuthorizationService = approvalAuthorizationService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader("X-Approval-Key");

        if (!approvalAuthorizationService.verifyApprovalHeader(header)) {
            throw new NotAuthorizedException("Header inválido: sem autorização para essa ação");
        }

        return true;
    }
}

/*
* GUIDE
* elimina a necessiade de HttpServletRequest em todos os endponints:
* ele barra a requisição com 401 antes de chegar no controller se o header estiver ausente/errado
* */