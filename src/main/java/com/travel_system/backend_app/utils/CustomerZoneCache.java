package com.travel_system.backend_app.utils;

import com.travel_system.backend_app.exceptions.CustomerNotFoundException;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.repository.CustomerRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CustomerZoneCache {

    private final CustomerRepository customerRepository;

    public CustomerZoneCache(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }


    @Cacheable(value = "timezones", key = "#customerId")
    public String getZoneId(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));

        return customer.getTimeZone();
    }

    @CacheEvict(value = "timezones", key = "#customerId")
    public void evict(UUID customerId) {
    }
}
