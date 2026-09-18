package com.travel_system.backend_app.utils;

import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.repository.PushNotificationDeviceTokenRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationRecipientResolver {
    private final PushNotificationDeviceTokenRepository deviceTokenRepository;

    public NotificationRecipientResolver(PushNotificationDeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    public Set<String> resolveAllCustomerUsers(UUID customerId) {
        Set<String> allTokens = new HashSet<>();

        allTokens.addAll(deviceTokenRepository.findTokensByCustomerIdStudents(customerId));
        allTokens.addAll(deviceTokenRepository.findTokensByCustomerIdDrivers(customerId));
        allTokens.addAll(deviceTokenRepository.findTokensByCustomerIdAdmins(customerId));
        allTokens.addAll(deviceTokenRepository.findTokensByCustomerIdResponsibles(customerId));

        return allTokens;
    }

    public Set<String> resolveCustomerStudents(UUID customerId) {
        return deviceTokenRepository.findTokensByCustomerIdStudents(customerId);
    }

    public Set<String> resolveCustomerDrivers(UUID customerId) {
        return deviceTokenRepository.findTokensByCustomerIdDrivers(customerId);
    }

    public Set<String> resolveCustomerAdmins(UUID customerId) {
        return deviceTokenRepository.findTokensByCustomerIdAdmins(customerId);
    }

    // notifica todos os responsibleAdult do customer
    public Set<String> resolveCustomerResponsibles(UUID customerId) {
        return deviceTokenRepository.findTokensByCustomerIdResponsibles(customerId);
    }

    // notifica apenas o customer do(s) estudante(s) especifico(s)
    public Set<String> resolveStudentResponsible(UUID studentId) {
        return deviceTokenRepository.findActiveTokensByStudentResponsible(studentId);
    }

    // notifica os responsibleAdult do customer que estão vinculados a estudantes de uma viagem
    public Set<String> resolveCustomerResponsiblesByTravel(UUID travelId) {
        return deviceTokenRepository.findActiveTokensByTravelResponsibles(travelId);
    }

    // usado para notificar um estudante de um período especifico
    public Set<String> resolvePeriodStudents(UUID customerId, Shift shift) {
        return deviceTokenRepository.findActiveTokensByCustomerAndShift(customerId, shift);
    }

    // usado para notificar apenas o STUDENT específico
    public Set<String> resolveSpecificStudent(UUID studentId) {
        return deviceTokenRepository.findActiveTokensBySpecificStudent(studentId);
    }

    // usado para notificar apenas o DRIVER específico
    public Set<String> resolveSpecificDriver(UUID driverId) {
        return deviceTokenRepository.findActiveTokensBySpecificDriver(driverId);
    }

    public Set<String> resolveTravelStudents(UUID travelId) {
        return deviceTokenRepository.findActiveTokensByTravelId(travelId);
    }

    public Set<String> resolveEmbarkedTravelStudents(UUID travelId) {
        return deviceTokenRepository.findActiveTokensByTravelIdAndEmbarkTrue(travelId);
    }

}
