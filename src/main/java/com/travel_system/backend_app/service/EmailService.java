package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.send_emails.SensitiveOperationCreatedEvent;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import com.travel_system.backend_app.utils.DateTimeFormats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    private final DateTimeFormats dateTimeFormats;

    @Value("${platform-admin.approval-email}")
    private String approvalEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender, DateTimeFormats dateTimeFormats) {
        this.mailSender = mailSender;
        this.dateTimeFormats = dateTimeFormats;
    }

    public void sendSensitiveOperationApprovalEmail(String pureToken, SensitiveOperationType type, Instant expiresAt) {
        String link = baseUrl + "/security/sensitive-operations/" + pureToken;

        String expiresAtFormated = dateTimeFormats.formatInstantDate(expiresAt);

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(approvalEmail);
        msg.setSubject("Aprovação necessária para: " + type);
        msg.setText("Uma operação sensível foi solicitada. \n\n" +
                "Tipo: " + type + "\n" +
                "Expira em: " + expiresAtFormated + "\n\n" +
                "Link para aprovação:\n " + link);

        mailSender.send(msg);
    }

    public void sendInvitationCreatedToUser(String userAccountEmail, String customerName,  Instant expiresAt, String pureToken) {
        String link = baseUrl + "/customer/invitation/" + pureToken;

        String expiresAtFormated = dateTimeFormats.formatInstantDate(expiresAt);

        String message = "Olá! Tudo bem?! \n \n" +
                "Você foi convidado para fazer parte de um novo lugar na Zyggo! confira as informações abaixo e, qualquer dúvida, entre em contato com a gente! \n\n" +
                "Nome: " + customerName + "\n\n" +
                "Expira em: " + expiresAtFormated + "\n\n" +
                "Clique para aceitar: " + link;

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(userAccountEmail);
        msg.setSubject("Novo convite!");
        msg.setText(message);

        mailSender.send(msg);
    }

    // email informativo
    public void sendInvitationAcceptedToUser(String userAccountEmail, String customerName,  Instant acceptedInvitationAt) {
        String acceptedInvitationAtFormated = dateTimeFormats.formatInstantDate(acceptedInvitationAt);

        String message = "Olá! Tudo bem?! \n \n" +
                "Você aceitou o convite para fazer parte de um novo lugar na Zyggo! Qualquer dúvida entre em contato com a gente! \n\n" +
                "Nome: " + customerName + "\n\n" +
                "Aceito em: " + acceptedInvitationAtFormated + "\n\n";

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(userAccountEmail);
        msg.setSubject("Convite Aceito");
        msg.setText(message);

        mailSender.send(msg);
    }

}
