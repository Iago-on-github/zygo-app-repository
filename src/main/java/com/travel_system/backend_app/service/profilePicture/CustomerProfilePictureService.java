package com.travel_system.backend_app.service.profilePicture;

import com.travel_system.backend_app.exceptions.CustomerNotFoundException;
import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Vehicle;
import com.travel_system.backend_app.repository.AdministratorRepository;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.service.ImageStorageService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class CustomerProfilePictureService {

    private final CustomerRepository customerRepository;
    private final AdministratorRepository administratorRepository;

    private final ImageStorageService imageStorageService;

    public CustomerProfilePictureService(CustomerRepository customerRepository, AdministratorRepository administratorRepository, ImageStorageService imageStorageService) {
        this.customerRepository = customerRepository;
        this.administratorRepository = administratorRepository;
        this.imageStorageService = imageStorageService;
    }

    @Transactional
    public void updateCustomerPicture(MultipartFile file) throws IOException {
        Customer customer = findCustomer();

        String key = "customers/" + customer.getId() + "/picture.jpeg";
        customer.setLogoUrl(imageStorageService.store(file, key));
    }

    @Transactional
    public void deleteCustomerPicture() {
        Customer customer = findCustomer();

        String currentKey = customer.getLogoUrl();
        if (currentKey == null) {
            return;
        }

        customer.setLogoUrl(null);
        imageStorageService.delete(currentKey);
    }

    private Customer findCustomer() {
        Administrator administrator = administratorRepository.findByEmail(getAuthenticatedUserEmail())
                .orElseThrow(() -> new AccessDeniedException("Administrador não encontrado"));

        return customerRepository.findById(administrator.getCustomerId())
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));
    }
}
