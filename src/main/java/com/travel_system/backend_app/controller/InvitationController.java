package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.annotations.RateLimited;
import com.travel_system.backend_app.interfaces.InvitationProfileData;
import com.travel_system.backend_app.interfaces.ProfileCreator;
import com.travel_system.backend_app.model.dtos.invitation.InvitationAcceptResponseDTO;
import com.travel_system.backend_app.model.dtos.invitation.MyInvitationResponseDTO;
import com.travel_system.backend_app.model.dtos.invitation.admin.AdministratorAcceptDTO;
import com.travel_system.backend_app.model.dtos.invitation.admin.AdministratorInvitationRequestDTO;
import com.travel_system.backend_app.model.dtos.invitation.driver.DriverAcceptDTO;
import com.travel_system.backend_app.model.dtos.invitation.driver.DriverInvitationRequestDTO;
import com.travel_system.backend_app.model.dtos.invitation.responsible.ResponsibleAdultAcceptDTO;
import com.travel_system.backend_app.model.dtos.invitation.responsible.ResponsibleInvitationRequestDTO;
import com.travel_system.backend_app.model.dtos.invitation.student.InstitutionCatalogResponseDTO;
import com.travel_system.backend_app.model.dtos.invitation.student.StudentAcceptDTO;
import com.travel_system.backend_app.model.dtos.invitation.student.StudentInvitationDTO;
import com.travel_system.backend_app.model.dtos.invitation.student.StudentInvitationRequestDTO;
import com.travel_system.backend_app.model.dtos.response.InvitationResponseDTO;
import com.travel_system.backend_app.model.enums.InvitationStatus;
import com.travel_system.backend_app.model.enums.RateLimitPolicy;
import com.travel_system.backend_app.model.enums.TargetUserType;
import com.travel_system.backend_app.service.InvitationService;
import com.travel_system.backend_app.service.strategies.invitation.AdministratorInvitationProfileStrategy;
import com.travel_system.backend_app.service.strategies.invitation.DriverInvitationProfileStrategy;
import com.travel_system.backend_app.service.strategies.invitation.ResponsibleInvitationProfileStrategy;
import com.travel_system.backend_app.service.strategies.invitation.StudentInvitationProfileStrategy;
import jakarta.validation.Valid;
import org.apache.coyote.Response;
import org.simpleframework.xml.Path;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/invitation")
public class InvitationController {

    private final InvitationService invitationService;

    private final StudentInvitationProfileStrategy studentInvitationStrategy;
    private final ResponsibleInvitationProfileStrategy responsibleInvitationProfileStrategy;
    private final DriverInvitationProfileStrategy driverInvitationProfileStrategy;
    private final AdministratorInvitationProfileStrategy administratorInvitationProfileStrategy;

    public InvitationController(InvitationService invitationService, StudentInvitationProfileStrategy studentInvitationStrategy, ResponsibleInvitationProfileStrategy responsibleInvitationProfileStrategy, DriverInvitationProfileStrategy driverInvitationProfileStrategy, AdministratorInvitationProfileStrategy administratorInvitationProfileStrategy) {
        this.invitationService = invitationService;
        this.studentInvitationStrategy = studentInvitationStrategy;
        this.responsibleInvitationProfileStrategy = responsibleInvitationProfileStrategy;
        this.driverInvitationProfileStrategy = driverInvitationProfileStrategy;
        this.administratorInvitationProfileStrategy = administratorInvitationProfileStrategy;
    }

    /*
    * ADMIN
    * Operações realizadas pelo adm:
    * - invite das entidades de domínio
    * - listagem dos invites realizados
    * - revogação de um invite realizado
    * - re-envio de um invite
    * */

    @RateLimited(RateLimitPolicy.SEND_INVITATION)
    @PostMapping("/students")
    public ResponseEntity<InvitationResponseDTO> sendStudentInvitation(@Valid @RequestBody StudentInvitationRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentInvitationStrategy.sendStudentInvitation(dto));
    }

    @RateLimited(RateLimitPolicy.SEND_INVITATION)
    @PostMapping("/responsibles")
    public ResponseEntity<InvitationResponseDTO> sendResponsibleInvitation(@Valid @RequestBody ResponsibleInvitationRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(responsibleInvitationProfileStrategy.sendResponsibleAdultInvitation(dto));
    }

    @RateLimited(RateLimitPolicy.SEND_INVITATION)
    @PostMapping("/drivers")
    public ResponseEntity<InvitationResponseDTO> sendDriverInvitation(@Valid @RequestBody DriverInvitationRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(driverInvitationProfileStrategy.sendDriverInvitation(dto));
    }

    @RateLimited(RateLimitPolicy.SEND_INVITATION)
    @PostMapping("/admins")
    public ResponseEntity<InvitationResponseDTO> sendAdministratorInvitation(@Valid @RequestBody AdministratorInvitationRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(administratorInvitationProfileStrategy.sendAdministratorInvitation(dto));
    }

    @GetMapping
    public ResponseEntity<Page<InvitationResponseDTO>> listCustomerInvitations(@RequestParam(required = false) InvitationStatus status, @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(invitationService.listCustomerInvitations(status, pageable));
    }

    @PatchMapping("/{invitationId}/revoke")
    public ResponseEntity<Void> revokeInvitation(@PathVariable UUID invitationId) {
        invitationService.revokeInvitation(invitationId);
        return ResponseEntity.noContent().build();
    }

    @RateLimited(RateLimitPolicy.SEND_INVITATION)
    @PatchMapping("/{invitationId}/resend")
    public ResponseEntity<InvitationResponseDTO> resendInvitation(@PathVariable UUID invitationId) {
        return ResponseEntity.ok(invitationService.resendInvitation(invitationId));
    }

    /*
     * CONVIDADO
     * Operações realizadas pelo convidado:
     * - listar invites pendentes
     * - aceitar um invite
     * - declinar um invite
     * (estudante tem endpoint para listar as instituições/cursos disponíveis)
     * */

    @GetMapping("/me/pending")
    public ResponseEntity<List<MyInvitationResponseDTO>> listMyPendingInvitations() {
        return ResponseEntity.ok(invitationService.listMyPendingInvitations());
    }

    @PatchMapping("/me/{invitationId}/decline")
    public ResponseEntity<Void> declineInvitation(@PathVariable UUID invitationId) {
        invitationService.declineInvitation(invitationId);
        return ResponseEntity.noContent().build();
    }

    /*
    * students
    * */
    @GetMapping("/{invitationId}/institutions")
    public ResponseEntity<List<InstitutionCatalogResponseDTO>> getInvitationInstitutions(@PathVariable UUID invitationId) {
        return ResponseEntity.ok().body(invitationService.getInvitationInstitutions(invitationId));
    }

    @PostMapping("/me/students/{invitationId}/accept")
    public ResponseEntity<InvitationAcceptResponseDTO> acceptStudentInvitation(@PathVariable UUID invitationId, @Valid @RequestBody StudentAcceptDTO dto) {
        return ResponseEntity.ok(studentInvitationStrategy.acceptStudentInvitation(invitationId, dto));
    }

    /*
    * administrators
    * */
    @PostMapping("/me/admins/{invitationId}/accept")
    public ResponseEntity<InvitationAcceptResponseDTO> acceptAdministratorInvitation(@PathVariable UUID invitationId, @Valid @RequestBody AdministratorAcceptDTO dto) {
        return ResponseEntity.ok(administratorInvitationProfileStrategy.acceptAdministratorInvitation(invitationId, dto));
    }

    /*
    * drives
    * */
    @PostMapping("/me/drivers/{invitationId}/accept")
    public ResponseEntity<InvitationAcceptResponseDTO> acceptDriverInvitation(@PathVariable UUID invitationId, @Valid @RequestBody DriverAcceptDTO dto) {
        return ResponseEntity.ok(driverInvitationProfileStrategy.acceptDriverInvitation(invitationId, dto));
    }

    /*
    * responsibles
    * */
    @PostMapping("/me/responsibles/{invitationId}/accept")
    public ResponseEntity<InvitationAcceptResponseDTO> acceptResponsibleInvitation(@PathVariable UUID invitationId, @Valid @RequestBody ResponsibleAdultAcceptDTO dto) {
        return ResponseEntity.ok().body(responsibleInvitationProfileStrategy.acceptResponsibleAdultInvitation(invitationId, dto));
    }

}
