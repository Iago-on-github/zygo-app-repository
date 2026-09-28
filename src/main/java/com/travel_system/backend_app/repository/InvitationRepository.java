package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Invitation;
import com.travel_system.backend_app.model.enums.InvitationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    boolean existsByInvitedUserAccountIdAndInvitationStatus(@Param("userAccountId") UUID userAccountId, @Param("InvitationStatus") InvitationStatus invitationStatus);

    @Query("SELECT inv FROM Invitation inv WHERE inv.customerId = :customerId AND :invitationStatus IS NULL OR inv.invitationStatus = :invitationStatus")
    Page<Invitation> findInvitationsByCustomerIdAndStatusOptional(
            @Param("customerId") UUID customerId,
            @Param("invitationStatus") InvitationStatus invitationStatus,
            Pageable pageable);

    @Query("SELECT inv FROM Invitation inv WHERE inv.invitedUserAccountId = :userAccountId AND inv.invitationStatus = 'PENDING' AND inv.expiresAt > :now ORDER BY createdAt DESC")
    List<Invitation> findMyPendingInvitations(@Param("userAccountId") UUID userAccountId, @Param("now") Instant now);

    @Query(value = """
        SELECT * FROM invitation_table
        WHERE id = :id AND invited_user_account_id = :userAccountId
        """, nativeQuery = true)
    Optional<Invitation> findByIdAndInvitedUserAccountIdIgnoringTenant(@Param("id") UUID id, @Param("userAccountId") UUID userAccountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE invitation_table
        SET invitation_status = 'CANCELLED', responded_at = :now
        WHERE invited_user_account_id = :userAccountId
          AND invitation_status = 'PENDING'
          AND id <> :acceptedId
        """, nativeQuery = true)
    int cancelOtherPendingInvitations(@Param("userAccountId") UUID userAccountId, @Param("acceptedId") UUID acceptedId, @Param("now") Instant now);

    @Modifying
    @Query(value = "UPDATE invitation_table SET invitation_status = 'EXPIRED', responded_at = :now WHERE invitation_status = 'PENDING' AND expires_at < :now .", nativeQuery = true)
    int expirePendingInvitationsWithoutTenantFilter(Instant now);

    boolean existsByCustomerIdAndInvitedUserAccountIdAndInvitationStatus(UUID customerId, UUID invitedUserAccountId, InvitationStatus invitationStatus);
}
