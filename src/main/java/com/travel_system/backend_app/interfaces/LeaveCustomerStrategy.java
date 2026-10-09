package com.travel_system.backend_app.interfaces;

import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.UserAccountType;

import java.util.UUID;

public interface LeaveCustomerStrategy {

    boolean supports(UserAccountType type);

    // regras do papel lança exceção se a saída não for permitida
    void validateLeave(UserAccount account);

    // transforma o perfil em histórico, sem vínculo com a conta
    void unlinkFromCustomer(UserAccount account);
}
