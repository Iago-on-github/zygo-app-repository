package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFound;
import com.travel_system.backend_app.model.UserModel;
import com.travel_system.backend_app.repository.UserRepository;
import com.travel_system.backend_app.utils.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        if (username == null || username.isBlank()) {
            throw new EmptyMandatoryFieldsFound("Email não informado.");
        }

        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        UserModel user = repository.findUserByEmail(username);

        if (user == null) {
            throw new EntityNotFoundException("Usuário não encontrado: " + username);
        }

        return new CustomUserDetails(user);
    }

}
