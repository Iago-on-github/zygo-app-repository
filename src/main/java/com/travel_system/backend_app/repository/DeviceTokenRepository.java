package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.DeviceToken;
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
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {
    Optional<DeviceToken> findDeviceTokenByToken(String token);

    @Modifying // Indica que é uma operação de escrita (UPDATE/DELETE)
    @Query("UPDATE DeviceToken dt SET dt.active = false WHERE dt.token IN :tokens")
    void deactivateTokensByValue(@Param("tokens") List<String> tokens);

    @Query("SELECT dt.token FROM DeviceToken dt WHERE dt.user.id= :userId AND dt.active = TRUE")
    Set<String> findTokensByUserId(@Param("userId") UUID userId);

    @Query("SELECT dt.token FROM DeviceToken dt WHERE dt.user.customerId = :customerId AND dt.active = TRUE")
    Set<String> findTokensByCustomerId(@Param("customerId") UUID customerId);

    // DISTINCT evita duplicidade de tokens caso o user tenha mais de uma ROLE
    @Query("SELECT DISTINCT dt.token FROM DeviceToken dt JOIN dt.user.permissions p WHERE dt.user.customerId = :customerId AND p.description = :role AND dt.active = TRUE")
    Set<String> findTokensByCustomerIdAndUserType(@Param("customerId") UUID customerId, @Param("role") String role);

    @Query("SELECT DISTINCT dt.token FROM DeviceToken dt JOIN dt.user.permissions p WHERE dt.user.customerId = :customerId AND p.description IN :roles AND dt.active = TRUE")
    Set<String> findActiveTokensByCustomerIdAndRoles(@Param("customerId") UUID customerId, @Param("roles") List<String> roles);

    @Query("SELECT DISTINCT dt.token FROM Travel t JOIN t.studentTravels st JOIN st.student s JOIN DeviceToken dt ON dt.user = s WHERE t.id = :travelId AND dt.active = TRUE")
    Set<String> findActiveTokensByTravelId(@Param("travelId") UUID travelId);

    @Query("SELECT DISTINCT dt.token FROM Travel t JOIN t.studentTravels st JOIN st.student s JOIN DeviceToken dt ON dt.user = s WHERE t.id = :travelId AND dt.active = TRUE AND st.embark = TRUE")
    Set<String> findActiveTokensByTravelIdAndEmbarkTrue(@Param("travelId") UUID travelId);
}
