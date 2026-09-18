package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.repository.UserAccountRepository;
import com.travel_system.backend_app.utils.CustomUserDetails;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService {
    private final UserAccountRepository repository;

    public UserService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Email não informado.");
        }

        UserAccount user = repository.findByEmailForAuthentication(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado com o e-mail: " + username));

        return new CustomUserDetails(user);
    }

}
