package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.service.CurrentUserService;
import org.apache.coyote.Response;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/v1/current")
public class CurrentUserController {
    private final CurrentUserService currentUserService;

    public CurrentUserController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateProfilePicture(Authentication auth, @RequestParam("file") MultipartFile file) throws IOException {
        String email = auth.getName();

        currentUserService.userProfilePictureUpdate(email, file);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/delete")
    public ResponseEntity<Void> deleteProfilePicture(Authentication auth) {
        String email = auth.getName();

        currentUserService.userProfilePictureDelete(email);
        return ResponseEntity.noContent().build();
    }
}
