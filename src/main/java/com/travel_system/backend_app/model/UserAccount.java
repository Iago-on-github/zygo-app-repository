package com.travel_system.backend_app.model;

import com.travel_system.backend_app.model.enums.UserAccountType;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "user_account_table",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_account_email", columnNames = "email"))
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserAccountType userAccountType;

    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)
    @JoinTable(name = "user_account_permissions", joinColumns = {@JoinColumn (name="id_account_id")},
    inverseJoinColumns = {@JoinColumn (name = "permission_id")})
    private List<Permissions> permissions = new ArrayList<>();

    @OneToMany(mappedBy = "userAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PushNotificationDeviceToken> PushNotificationDeviceTokens = new HashSet<>();

    public UserAccount() {
    }

    public UserAccount(UUID id, String email, String password, UserAccountType userAccountType) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.userAccountType = userAccountType;
    }

    public List<String> getRoles() {
        List<String> roles = new ArrayList<>();
        for (Permissions permission : permissions) {
            roles.add(permission.getDescription());
        }
        return roles;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserAccountType getUserAccountType() {
        return userAccountType;
    }

    public void setUserAccountType(UserAccountType userAccountType) {
        this.userAccountType = userAccountType;
    }

    public List<Permissions> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<Permissions> permissions) {
        this.permissions = permissions;
    }

    public Set<PushNotificationDeviceToken> getPushNotificationDeviceTokens() {
        return PushNotificationDeviceTokens;
    }

    public void setPushNotificationDeviceTokens(Set<PushNotificationDeviceToken> pushNotificationDeviceTokens) {
        PushNotificationDeviceTokens = pushNotificationDeviceTokens;
    }
}


/*
 * entidade global de autenticação, responde a pergunta de "quem está tentando se autenticar?"
 * */