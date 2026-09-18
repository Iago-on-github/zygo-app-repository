package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.send_emails.SensitiveOperationCreatedEvent;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${platform-admin.approval-email}")
    private String approvalEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendSensitiveOperationApprovalEmail(String pureToken, SensitiveOperationType type, Instant expiresAt) {
        String link = baseUrl + "/security/sensitive-operations/" + pureToken;

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(approvalEmail);
        msg.setSubject("Aprovação necessária para: " + type);
        msg.setText("Uma operação sensível foi solicitada. \n\n" +
                "Tipo: " + type + "\n" +
                "Expira em: " + expiresAt + "\n\n" +
                "Link para aprovação:\n " + link);

        mailSender.send(msg);
    }

}
