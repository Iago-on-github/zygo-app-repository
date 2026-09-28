package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.Invitation;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.response.InvitationResponseDTO;
import com.travel_system.backend_app.model.enums.InvitationStatus;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring", imports = InvitationStatus.class)
public interface InvitationResponseMapper {

    @Mapping(target = "id", source = "invitation.id")
    @Mapping(target = "invitedByName", source = "invitedByName")
    @Mapping(target = "invitationStatus",
            expression = "java(invitation.isPending() && invitation.isExpired(now) ? InvitationStatus.EXPIRED : invitation.getInvitationStatus())")
    InvitationResponseDTO toResponse(Invitation invitation, String invitedByName, @Context Instant now);
}
