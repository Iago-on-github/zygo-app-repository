package com.travel_system.backend_app.interfaces;

import com.travel_system.backend_app.model.Invitation;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.UserAccount;

import java.util.UUID;

@FunctionalInterface
public interface ProfileCreator {

    UUID create(Invitation invitation, UserAccount account);
}
