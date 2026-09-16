package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.NotificationAudience;
import com.travel_system.backend_app.model.enums.Platform;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "push_notification_device_tokens")
public class PushNotificationDeviceToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;
    @Column(nullable = false, unique = true)
    private String token;
    @Enumerated(EnumType.STRING)
    private Platform platform;
    @Column(nullable = false)
    private boolean active = true;
    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public PushNotificationDeviceToken() {
    }

    public PushNotificationDeviceToken(UUID id, UserAccount userAccount, String token, Platform platform, boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userAccount = userAccount;
        this.token = token;
        this.platform = platform;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
