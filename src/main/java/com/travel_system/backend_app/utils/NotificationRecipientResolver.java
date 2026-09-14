package com.travel_system.backend_app.utils;

import com.travel_system.backend_app.repository.PushNotificationDeviceTokenRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationRecipientResolver {
    private final PushNotificationDeviceTokenRepository deviceTokenRepository;

    public NotificationRecipientResolver(PushNotificationDeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    // notificar um student/driver/admin específico
    public Set<String> resolveSpecificUser(UUID userAccountId) {
        return deviceTokenRepository.findTokensByUserId(userAccountId);
    }

    // notificação geral do customer
    public Set<String> resolveAllCustomerUsers(UUID customerId)  {
        return deviceTokenRepository.findTokensByCustomerId();
    }

    // notifica somente students
    public Set<String> resolveCustomerStudents(UUID customerId) {
        String studentRole = "ROLE_USER";
        return deviceTokenRepository.findTokensByCustomerIdAndUserType(studentRole);
    }

    // notifica somente drivers
    public Set<String> resolveCustomerDrivers(UUID customerId) {
        String driverRole = "ROLE_DRIVER";
        return deviceTokenRepository.findTokensByCustomerIdAndUserType(driverRole);
    }

    // notifica somente admins
    public Set<String> resolveCustomerAdmins(UUID customerId) {
        String adminRole = "ROLE_ADMIN";
        return deviceTokenRepository.findTokensByCustomerIdAndUserType(adminRole);
    }

    // notifica os alunos vinculados a uma viagem
    public Set<String> resolveTravelStudents(UUID travelId) {
        return deviceTokenRepository.findActiveTokensByTravelId(travelId);
    }

    // notifica os alunos vinculados e embarcados em uma viagem
    public Set<String> resolveEmbarkedTravelStudents(UUID travelId) {
        return deviceTokenRepository.findActiveTokensByTravelIdAndEmbarkTrue(travelId);
    }

}
