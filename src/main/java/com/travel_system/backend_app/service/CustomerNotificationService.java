package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.dtos.notifications.SystemPushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.model.enums.SystemNotificationAudience;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomerNotificationService {

    private final FirebaseNotificationSender firebaseNotificationSender;

    public CustomerNotificationService(FirebaseNotificationSender firebaseNotificationSender) {
        this.firebaseNotificationSender = firebaseNotificationSender;
    }

    public void sendConfirmTimezoneNotificationToAdmin(List<UUID> allByTimeZoneConfirmedFalse) {
        SystemNotificationAudience allCustomerAdmins = SystemNotificationAudience.ALL_CUSTOMER_ADMINS;

        for (UUID customerId : allByTimeZoneConfirmedFalse) {

            String title = "Confirmação de fuso pendente";
            String message = "O fuso horário do seu espaço está com a configuração padrão (São Paulo - UTC-3). Se a sua cidade usa outro fuso horário, altere nas configurações: ele é usado em todos os prazos e lembretes do sistema.";
            String link = "/customer/settings/timezone";

            Map<String, String> data = Map.of(
                    "eventType", "REQUIRE_TIMEZONE_CONFIRM",
                    "customerId", customerId.toString()
            );

            SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(allCustomerAdmins, customerId, null, null, null, null, title, message, link, Priority.HIGH, data);

            firebaseNotificationSender.sendSystemNotification(command);
        }

    }
}
