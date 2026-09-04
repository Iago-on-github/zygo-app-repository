package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.PushNotificationDeviceToken;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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

    @Query("SELECT dt.token FROM PushNotificationDeviceToken dt WHERE dt.userAccount.id= :userId AND dt.active = TRUE")
    Set<String> findTokensByUserId(@Param("userId") UUID userId);

    @Query("SELECT dt.token FROM PushNotificationDeviceToken dt WHERE dt.active = TRUE")
    Set<String> findTokensByCustomerId();

    @Query("SELECT DISTINCT dt.token FROM PushNotificationDeviceToken dt JOIN dt.userAccount.permissions p WHERE p.description = :role AND dt.active = TRUE")
    Set<String> findTokensByCustomerIdAndUserType(@Param("role") String role);

    @Query("SELECT DISTINCT dt.token FROM PushNotificationDeviceToken dt JOIN dt.userAccount.permissions p WHERE p.description IN :roles AND dt.active = TRUE")
    Set<String> findActiveTokensByCustomerIdAndRoles(@Param("roles") List<String> roles);
    @Query("SELECT DISTINCT dt.token FROM Travel t " +
            "JOIN t.studentTravels st " +
            "JOIN st.student s " +
            "JOIN PushNotificationDeviceToken dt ON dt.userAccount = s.userAccount " +
            "WHERE t.id = :travelId AND dt.active = TRUE")
    Set<String> findActiveTokensByTravelId(@Param("travelId") UUID travelId);

    @Query("SELECT DISTINCT dt.token FROM Travel t " +
            "JOIN t.studentTravels st " +
            "JOIN st.student s " +
            "JOIN PushNotificationDeviceToken dt ON dt.userAccount = s.userAccount " +
            "WHERE t.id = :travelId AND dt.active = TRUE AND st.embark = TRUE")
    Set<String> findActiveTokensByTravelIdAndEmbarkTrue(@Param("travelId") UUID travelId);
}
