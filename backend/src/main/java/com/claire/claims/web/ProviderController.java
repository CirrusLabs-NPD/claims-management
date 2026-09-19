package com.claire.claims.web;

import com.claire.claims.dto.ReferenceDtos.*;
import com.claire.claims.service.ReferenceDataService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ReferenceDataService service;

    public ProviderController(ReferenceDataService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProviderResponse> list() {
        return service.listProviders();
    }

    @GetMapping("/{id}")
    public ProviderResponse get(@PathVariable Long id) {
        return service.getProvider(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProviderResponse> create(@Valid @RequestBody ProviderRequest request) {
        ProviderResponse created = service.createProvider(request);
        return ResponseEntity.created(URI.create("/api/providers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProviderResponse update(@PathVariable Long id, @Valid @RequestBody ProviderRequest request) {
        return service.updateProvider(id, request);
    }
}
