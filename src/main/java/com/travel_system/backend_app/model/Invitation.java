package com.travel_system.backend_app.model;

import com.travel_system.backend_app.exceptions.InvalidInvitationProcessException;
import com.travel_system.backend_app.exceptions.InvitationNotPendingException;
import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.InvitationStatus;
import com.travel_system.backend_app.model.enums.TargetUserType;
import jakarta.persistence.*;

import javax.annotation.processing.Generated;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invitation_table")
public class Invitation extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID invitedUserAccountId;
    private String invitedIdentifier;
    @Enumerated(EnumType.STRING)
    private TargetUserType targetUserType;
    @Enumerated(EnumType.STRING)
    private InvitationStatus invitationStatus;
    private String profileData;
    @Column(unique = true)
    private String verificationTokenHash;
    private UUID invitedBy;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant respondedAt;
    @Version
    private Long version;

    public Invitation() {
    }

    public Invitation(UUID id, UUID invitedUserAccountId, String invitedIdentifier, TargetUserType targetUserType, InvitationStatus invitationStatus, String profileData, String verificationTokenHash, UUID invitedBy, Instant expiresAt, Instant createdAt, Instant respondedAt, Long version) {
        this.id = id;
        this.invitedUserAccountId = invitedUserAccountId;
        this.invitedIdentifier = invitedIdentifier;
        this.targetUserType = targetUserType;
        this.invitationStatus = invitationStatus;
        this.profileData = profileData;
        this.verificationTokenHash = verificationTokenHash;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.respondedAt = respondedAt;
        this.version = version;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isPending() {
        return invitationStatus == InvitationStatus.PENDING;
    }

    public void accept(Instant now) {
        if (invitationStatus != InvitationStatus.PENDING) {
            throw new InvitationNotPendingException("Status precisa estar pendente para concluir a operação de accept.");
        }

        this.invitationStatus = InvitationStatus.ACCEPTED;
        respondedAt = now;
    }

    public void decline(Instant now) {
        if (invitationStatus != InvitationStatus.PENDING) {
            throw new InvitationNotPendingException("Status precisa estar pendente para concluir a operação de decline.");
        }

        this.invitationStatus = InvitationStatus.DECLINED;
        respondedAt = now;
    }

    public void revoke(Instant now) {
        if (invitationStatus != InvitationStatus.PENDING) {
            throw new InvitationNotPendingException("Status precisa estar pendente para concluir a operação de revoke.");
        }

        this.invitationStatus = InvitationStatus.REVOKED;
        respondedAt = now;
    }

    public void markExpired(Instant now) {
        if (invitationStatus != InvitationStatus.PENDING) {
            throw new InvitationNotPendingException("Status precisa estar pendente para concluir a operação de markExpired.");
        }

        this.invitationStatus = InvitationStatus.EXPIRED;
        respondedAt = now;
    }

    public void renew(String newHashToken, Instant newExpiresAt) {
        if (!(invitationStatus == InvitationStatus.PENDING || invitationStatus == InvitationStatus.EXPIRED)) {
            throw new InvalidInvitationProcessException("Status precisa obrigatoriamente ser pending ou expired para a operação de renew");
        }

        this.verificationTokenHash = newHashToken;
        this.expiresAt = newExpiresAt;
        this.respondedAt = null;

        this.invitationStatus = InvitationStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getInvitedUserAccountId() {
        return invitedUserAccountId;
    }

    public void setInvitedUserAccountId(UUID invitedUserAccountId) {
        this.invitedUserAccountId = invitedUserAccountId;
    }

    public String getInvitedIdentifier() {
        return invitedIdentifier;
    }

    public void setInvitedIdentifier(String invitedIdentifier) {
        this.invitedIdentifier = invitedIdentifier;
    }

    public TargetUserType getTargetUserType() {
        return targetUserType;
    }

    public void setTargetUserType(TargetUserType targetUserType) {
        this.targetUserType = targetUserType;
    }

    public InvitationStatus getInvitationStatus() {
        return invitationStatus;
    }

    public void setInvitationStatus(InvitationStatus invitationStatus) {
        this.invitationStatus = invitationStatus;
    }

    public String getProfileData() {
        return profileData;
    }

    public void setProfileData(String profileData) {
        this.profileData = profileData;
    }

    public String getVerificationTokenHash() {
        return verificationTokenHash;
    }

    public void setVerificationTokenHash(String verificationTokenHash) {
        this.verificationTokenHash = verificationTokenHash;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(UUID invitedBy) {
        this.invitedBy = invitedBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
