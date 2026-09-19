package com.claire.claims.service;

import com.claire.claims.domain.AppUser;
import com.claire.claims.dto.AuthDtos.*;
import com.claire.claims.repository.AppUserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AppUser user = users.findByUsername(request.username())
                // Same exception for unknown user and wrong password: do not
                // let the error message confirm which usernames exist.
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        if (!user.isEnabled()) {
            throw new DisabledException("This account is disabled");
        }

        TokenService.IssuedToken token = tokens.issue(user);
        return new LoginResponse(token.value(), user.getUsername(), user.getFullName(),
                                 user.getRole(), token.expiresAt());
    }

    @Transactional(readOnly = true)
    public CurrentUser currentUser(String username) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Unknown user"));
        return new CurrentUser(user.getUsername(), user.getFullName(), user.getRole());
    }
}
