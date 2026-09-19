package com.claire.claims.web;

import com.claire.claims.dto.AuthDtos.*;
import com.claire.claims.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(auth.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUser> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(auth.currentUser(jwt.getSubject()));
    }
}
