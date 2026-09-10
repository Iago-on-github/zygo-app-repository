package com.travel_system.backend_app.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ApprovalAuthorizationService {

    @Value("${security.approval.secret-key}")
    private String approvalSecretHeader;

    public boolean verifyApprovalHeader(String token) {

        // se existir, valida
        if (token != null && !token.isBlank()) {
            return token.equalsIgnoreCase(approvalSecretHeader);
        }

        return false;
    }

}

/*
* GUIDE:
* realiza a valdação de presença da secret key no header durante o processo de review / approve / execute
*
* */
