package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.enums.Platform;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notifications")
public class FirebaseNotificationController {
    private final FirebaseNotificationSender firebaseNotificationSender;

    public FirebaseNotificationController(FirebaseNotificationSender firebaseNotificationSender) {
        this.firebaseNotificationSender = firebaseNotificationSender;
    }

    @PostMapping
    public ResponseEntity<Void> manageUserToken(Authentication auth, @RequestBody String webBrowserToken) {
        String userEmail = auth.getName();

        firebaseNotificationSender.manageUserToken(userEmail, webBrowserToken, Platform.WEB);

        return ResponseEntity.accepted().build();
    }
}
