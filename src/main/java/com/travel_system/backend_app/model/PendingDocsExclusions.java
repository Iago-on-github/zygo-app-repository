package com.travel_system.backend_app.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_docs_exclusions_table")
@EntityListeners(AuditingEntityListener.class)
public class PendingDocsExclusions {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(unique = true)
    private String storageKey;
    private long attempts;
    @Column(length = 50)
    private String lastErrorMsg;
    @CreatedDate
    private Instant createdAt;

    public PendingDocsExclusions() {
    }

    public PendingDocsExclusions(UUID id, String storageKey, long attempts, String lastErrorMsg, Instant createdAt) {
        this.id = id;
        this.storageKey = storageKey;
        this.attempts = attempts;
        this.lastErrorMsg = lastErrorMsg;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public long getAttempts() {
        return attempts;
    }

    public void setAttempts(long attempts) {
        this.attempts = attempts;
    }

    public String getLastErrorMsg() {
        return lastErrorMsg;
    }

    public void setLastErrorMsg(String lastErrorMsg) {
        this.lastErrorMsg = lastErrorMsg;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
