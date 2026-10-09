package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.service.profilePicture.UserProfilePictureService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RequestMapping("/profile/picture")
@RestController
public class UserProfilePictureController {

    private final UserProfilePictureService userProfilePictureService;

    public UserProfilePictureController(UserProfilePictureService userProfilePictureService) {
        this.userProfilePictureService = userProfilePictureService;
    }

    @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateProfilePicture(@RequestParam("file") MultipartFile file) throws IOException {
        userProfilePictureService.updateMyProfilePicture(file);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/delete")
    public ResponseEntity<Void> deleteProfilePicture() {
        userProfilePictureService.deleteMyProfilePicture();
        return ResponseEntity.noContent().build();
    }
}
