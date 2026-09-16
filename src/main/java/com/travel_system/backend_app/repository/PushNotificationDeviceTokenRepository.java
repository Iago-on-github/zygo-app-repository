package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.PushNotificationDeviceToken;
import com.travel_system.backend_app.model.enums.Shift;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.keyvalue.repository.config.QueryCreatorType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface PushNotificationDeviceTokenRepository extends JpaRepository<PushNotificationDeviceToken, UUID> {
    Optional<PushNotificationDeviceToken> findByToken(String token);

    @Modifying // Indica que é uma operação de escrita (UPDATE/DELETE)
    @Query("UPDATE PushNotificationDeviceToken dt SET dt.active = false WHERE dt.token IN :tokens")
    void deactivateTokensByValue(@Param("tokens") List<String> tokens);

    @Query("""
        SELECT DISTINCT dt.token
        FROM PushNotificationDeviceToken dt
        WHERE dt.active = TRUE
          AND EXISTS (
              SELECT 1
              FROM Student s
              WHERE s.id = :studentId
                AND s.userAccount = dt.userAccount
          )
        """)
    Set<String> findActiveTokensBySpecificStudent(
            @Param("studentId") UUID studentId
    );

    @Query("""
        SELECT DISTINCT dt.token
        FROM PushNotificationDeviceToken dt
        WHERE dt.active = TRUE
          AND EXISTS (
              SELECT 1
              FROM Student s
              WHERE s.customerId = :customerId
                AND s.userAccount = dt.userAccount
          )
        """)
    Set<String> findTokensByCustomerIdStudents(
            @Param("customerId") UUID customerId
    );

    @Query("""
        SELECT DISTINCT dt.token
        FROM PushNotificationDeviceToken dt
        WHERE dt.active = TRUE
          AND EXISTS (
              SELECT 1
              FROM Driver d
              WHERE d.customerId = :customerId
                AND d.userAccount = dt.userAccount
          )
        """)
    Set<String> findTokensByCustomerIdDrivers(
            @Param("customerId") UUID customerId
    );

    @Query("""
        SELECT DISTINCT dt.token
        FROM PushNotificationDeviceToken dt
        WHERE dt.active = TRUE
          AND EXISTS (
              SELECT 1
              FROM Administrator a
              WHERE a.customerId = :customerId
                AND a.userAccount = dt.userAccount
          )
        """)
    Set<String> findTokensByCustomerIdAdmins(
            @Param("customerId") UUID customerId
    );

    @Query("""
        SELECT DISTINCT dt.token
        FROM PushNotificationDeviceToken dt
        WHERE dt.active = TRUE
          AND EXISTS (
              SELECT 1
              FROM ResponsibleAdult r
              WHERE r.customerId = :customerId
                AND r.userAccount = dt.userAccount
          )
        """)
    Set<String> findTokensByCustomerIdResponsibles(
            @Param("customerId") UUID customerId
    );

    @Query("""
        SELECT DISTINCT dt.token
        FROM PushNotificationDeviceToken dt
        WHERE dt.active = TRUE
          AND EXISTS (
              SELECT 1
              FROM Student s
              JOIN s.responsibleAdult r
              WHERE s.id = :studentId
                AND r.userAccount = dt.userAccount
          )
        """)
    Set<String> findActiveTokensByStudentResponsible(
            @Param("studentId") UUID studentId
    );

    @Query("""
        SELECT DISTINCT dt.token
        FROM PushNotificationDeviceToken dt
        WHERE dt.active = TRUE
          AND EXISTS (
              SELECT 1
              FROM Driver d
              WHERE d.id = :driverId
                AND d.userAccount = dt.userAccount
          )
        """)
    Set<String> findActiveTokensBySpecificDriver(
            @Param("driverId") UUID driverId
    );

    @Query("""
        SELECT DISTINCT dt.token
        FROM PushNotificationDeviceToken dt
        WHERE dt.active = TRUE
          AND EXISTS (
              SELECT 1
              FROM Travel t
              JOIN t.studentTravels st
              JOIN st.student s
              WHERE t.id = :travelId
                AND s.userAccount = dt.userAccount
          )
        """)
    Set<String> findActiveTokensByTravelId(
            @Param("travelId") UUID travelId
    );

    @Query("""
        SELECT DISTINCT dt.token
        FROM PushNotificationDeviceToken dt
        WHERE dt.active = TRUE
          AND EXISTS (
              SELECT 1
              FROM Student s
              WHERE s.customerId = :customerId
                AND :shift MEMBER OF s.studentShift
                AND s.userAccount = dt.userAccount
          )
        """)
    Set<String> findActiveTokensByCustomerAndShift(
            @Param("customerId") UUID customerId,
            @Param("shift") Shift shift
    );

    @Query("""
        SELECT DISTINCT dt.token
        FROM PushNotificationDeviceToken dt
        WHERE dt.active = TRUE
          AND EXISTS (
              SELECT 1
              FROM Travel t
              JOIN t.studentTravels st
              JOIN st.student s
              JOIN s.responsibleAdult r
              WHERE t.id = :travelId
                AND r.userAccount = dt.userAccount
          )
        """)
    Set<String> findActiveTokensByTravelResponsibles(
            @Param("travelId") UUID travelId
    );

    @Query("""
        SELECT DISTINCT dt.token
        FROM PushNotificationDeviceToken dt
        WHERE dt.active = TRUE
          AND EXISTS (
              SELECT 1
              FROM Travel t
              JOIN t.studentTravels st
              JOIN st.student s
              WHERE t.id = :travelId
                AND st.embark = TRUE
                AND s.userAccount = dt.userAccount
          )
        """)
    Set<String> findActiveTokensByTravelIdAndEmbarkTrue(
            @Param("travelId") UUID travelId
    );
}


/*
* notificações a se fazer:
* - alunos embarcados
* - notificações por viagem específica (embarcados) e apenas vinculados
* -
* */