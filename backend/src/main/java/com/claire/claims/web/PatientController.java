package com.claire.claims.web;

import com.claire.claims.dto.ClaimDtos.PageResponse;
import com.claire.claims.dto.ReferenceDtos.*;
import com.claire.claims.service.ReferenceDataService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final ReferenceDataService service;

    public PatientController(ReferenceDataService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PatientSummary> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return service.searchPatients(q, pageable);
    }

    @GetMapping("/{id}")
    public PatientResponse get(@PathVariable Long id) {
        return service.getPatient(id);
    }

    @GetMapping("/{id}/policies")
    public List<PolicyResponse> policies(@PathVariable Long id) {
        return service.policiesForPatient(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientRequest request) {
        PatientResponse created = service.createPatient(request);
        return ResponseEntity.created(URI.create("/api/patients/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public PatientResponse update(@PathVariable Long id, @Valid @RequestBody PatientRequest request) {
        return service.updatePatient(id, request);
    }
}
