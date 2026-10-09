package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.security.AuthTokens;
import com.travel_system.backend_app.service.CustomerMembershipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me")
public class CurrentUserController {

    private final CustomerMembershipService customerMembershipService;

    public CurrentUserController(CustomerMembershipService customerMembershipService) {
        this.customerMembershipService = customerMembershipService;
    }

    @PostMapping("/customer/leave")
    public ResponseEntity<AuthTokens> leaveCustomer() {
        return ResponseEntity.ok().body(customerMembershipService.leaveCustomer());
    }
}
