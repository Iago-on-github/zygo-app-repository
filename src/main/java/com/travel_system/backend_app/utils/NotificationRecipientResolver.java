package com.travel_system.backend_app.utils;

import com.travel_system.backend_app.exceptions.DomainValidationException;
import com.travel_system.backend_app.model.dtos.notifications.SystemPushNotificationCommandDTO;
import com.travel_system.backend_app.model.dtos.notifications.TravelPushNotificationCommandDTO;
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

    /*
    * SYSTEM NOTIFICATION
    * */

    public Set<String> resolveSpecificAdmin(UUID adminId) {
        return deviceTokenRepository.findActiveTokensBySpecificAdmin(adminId);
    }

    public Set<String> resolveSpecificUserAccount(UUID userAccountId) {
        return deviceTokenRepository.findActiveTokensBySpecificUserAccount(userAccountId);
    }


    // separa cada tokem com base na audiência dele (a quem deve ser enviado)
    protected Set<String> resolve(TravelPushNotificationCommandDTO command) {
        if (command == null || command.travelNotificationAudience() == null) {
            throw new DomainValidationException("[resolveTravelTokensByAudience] audience não pode ser null");
        }

        return switch (command.travelNotificationAudience()) {
            case CUSTOMER_RESPONSIBLES -> {
                if (command.customerId() == null) {
                    throw new DomainValidationException("[resolveTravelTokensByAudience] customerId obrigatório para CUSTOMER_RESPONSIBLES");
                }
                yield resolveCustomerResponsibles(command.customerId());
            }

            case ALL_CUSTOMER_USERS -> {
                if (command.customerId() == null) {
                    throw new DomainValidationException("[resolveTravelTokensByAudience] customerId obrigatório para ALL_CUSTOMER_USERS");
                }
                yield resolveAllCustomerUsers(command.customerId());
            }

            case CUSTOMER_STUDENTS -> {
                if (command.customerId() == null) {
                    throw new DomainValidationException("[resolveTravelTokensByAudience] customerId obrigatório para CUSTOMER_STUDENTS");
                }

                yield resolveCustomerStudents(command.customerId());
            }

            case CUSTOMER_DRIVERS -> {
                if (command.customerId() == null) {
                    throw new DomainValidationException("[resolveTravelTokensByAudience] customerId obrigatório para CUSTOMER_DRIVERS");
                }

                yield resolveCustomerDrivers(command.customerId());
            }

            case CUSTOMER_ADMINS -> {
                if (command.customerId() == null) {
                    throw new DomainValidationException("[resolveTravelTokensByAudience] customerId obrigatório para CUSTOMER_ADMINS");
                }

                yield resolveCustomerAdmins(command.customerId());
            }

            case SPECIFIC_STUDENT -> {
                if (command.studentId() == null) {
                    throw new DomainValidationException("[resolveTravelTokensByAudience] studentId obrigatório para SPECIFIC_STUDENT");
                }

                yield resolveSpecificStudent(command.studentId());
            }

            case SPECIFIC_DRIVER -> {
                if (command.driverId() == null) {
                    throw new DomainValidationException("[resolveTravelTokensByAudience] driverId obrigatório para SPECIFIC_DRIVER");
                }

                yield resolveSpecificDriver(command.driverId());
            }

            case PERIOD_STUDENTS -> {
                if (command.customerId() == null || command.shift() == null) {
                    throw new DomainValidationException("[resolveTravelTokensByAudience] customerId e shift obrigatórios para PERIOD_STUDENTS");
                }

                yield resolvePeriodStudents(command.customerId(), command.shift());
            }

            case TRAVEL_STUDENTS -> {
                if (command.travelId() == null) {
                    throw new DomainValidationException("[resolveTravelTokensByAudience] travelId obrigatório para TRAVEL_STUDENTS");
                }

                yield resolveTravelStudents(command.travelId());
            }

            case TRAVEL_RESPONSIBLES -> {
                if (command.travelId() == null) {
                    throw new DomainValidationException("[resolveTravelTokensByAudience] travelId obrigatório para TRAVEL_RESPONSIBLES");
                }

                yield resolveCustomerResponsiblesByTravel(command.travelId());
            }

            case STUDENT_RESPONSIBLE -> {
                if (command.studentId() == null) {
                    throw new DomainValidationException("[resolveTravelTokensByAudience] studentId obrigatório para STUDENT_RESPONSIBLE");
                }

                yield resolveStudentResponsible(command.studentId());
            }

            case EMBARKED_TRAVEL_STUDENTS -> {
                if (command.travelId() == null) {
                    throw new DomainValidationException("[resolveTravelTokensByAudience] travelId obrigatório para EMBARKED_TRAVEL_STUDENTS");
                }

                yield resolveEmbarkedTravelStudents(command.travelId());
            }
        };
    }

    // system resolver
    protected Set<String> resolve(SystemPushNotificationCommandDTO command) {
        if (command == null || command.systemNotificationAudience() == null) {
            throw new DomainValidationException("[resolveSystemTokensBuAudience] audience não pode ser null");
        }

        return switch (command.systemNotificationAudience()) {
            case SPECIFIC_CUSTOMER_ADMIN -> {
                if (command.adminId() == null) {
                    throw new DomainValidationException("[resolveSystemTokensBuAudience] adminId obrigatório para SPECIFIC_CUSTOMER_ADMIN");
                }

                yield  resolveSpecificAdmin(command.adminId());
            } case SPECIFIC_USER_ACCOUNT -> {
                if (command.userAccountId() == null) {
                    throw new DomainValidationException("[resolveSystemTokensBuAudience] userAccoundId obrigatório para SPECIFIC_USER_ACCOUNT");
                }

                yield resolveSpecificUserAccount(command.userAccountId());
            }
        };
    }

}
