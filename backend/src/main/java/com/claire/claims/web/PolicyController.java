package com.claire.claims.web;

import com.claire.claims.dto.ReferenceDtos.*;
import com.claire.claims.service.ReferenceDataService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final ReferenceDataService service;

    public PolicyController(ReferenceDataService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<PolicyResponse> create(@Valid @RequestBody PolicyRequest request) {
        PolicyResponse created = service.createPolicy(request);
        return ResponseEntity.created(URI.create("/api/policies/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public PolicyResponse update(@PathVariable Long id, @Valid @RequestBody PolicyRequest request) {
        return service.updatePolicy(id, request);
    }
}
