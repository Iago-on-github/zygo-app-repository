package com.travel_system.backend_app.model.dtos.ownApi;

import com.travel_system.backend_app.model.Customer;

/*
 * configurações de prazos da matrícula do Customer
 * */

public record RegistrationSettings(int gracePeriodDays, int renewalWindowDays, int blockWarningDays) {

    public static RegistrationSettings from(Customer customer) {
        return new RegistrationSettings(
                customer.getRegistrationGracePeriodDays(),
                customer.getRegistrationRenewalWindowDays(),
                customer.getRegistrationBlockWarningDays());
    }
}
