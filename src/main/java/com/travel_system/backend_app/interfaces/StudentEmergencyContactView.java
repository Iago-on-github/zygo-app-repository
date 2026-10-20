package com.travel_system.backend_app.interfaces;

import java.util.UUID;

/*
* usado no repo do responsible p/ contatos de emergência dos alunos menores, em lote
 * */

public interface StudentEmergencyContactView {
    UUID getStudentId();
    String getName();
    String getTelephone();
}
