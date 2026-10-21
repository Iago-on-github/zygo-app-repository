package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.response.HealthPendingItemsResponseDTO;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.service.HealthPendingItemsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/pending-items")
public class HealthPendingItemsController {

    private final HealthPendingItemsService healthPendingItemsService;

    public HealthPendingItemsController(HealthPendingItemsService healthPendingItemsService) {
        this.healthPendingItemsService = healthPendingItemsService;
    }

    /*
    * endpoints para estudantes e motoristas
    * */

    @GetMapping("/me")
    public ResponseEntity<HealthPendingItemsResponseDTO> getMyPendingItems() {
        return ResponseEntity.ok().body(healthPendingItemsService.getMyPendingItems());
    }

    /*
    * endpoints para responsáveis (aluno menor)
    * */

    @GetMapping("/students/{studentId}")
    public ResponseEntity<HealthPendingItemsResponseDTO> getStudentPendingItems(@PathVariable UUID studentId) {
        return ResponseEntity.ok().body(healthPendingItemsService.getStudentPendingItems(studentId));
    }

    /*
    * endpoints para admin
    * */

    @GetMapping("/admin/{ownerType}/{ownerId}")
    public ResponseEntity<HealthPendingItemsResponseDTO>  getOwnerPendingItems(@PathVariable OwnerType ownerType, @PathVariable UUID ownerId) {
        return ResponseEntity.ok().body(healthPendingItemsService.getOwnerPendingItems(ownerType, ownerId));
    }
}
