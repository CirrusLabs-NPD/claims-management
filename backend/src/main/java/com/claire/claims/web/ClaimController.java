package com.claire.claims.web;

import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.dto.ClaimDtos.*;
import com.claire.claims.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService claims;

    public ClaimController(ClaimService claims) {
        this.claims = claims;
    }

    /**
     * Paged, filtered claims list.
     * All filters are optional and combine with AND.
     */
    @GetMapping
    public PageResponse<ClaimListItem> list(
            @RequestParam(required = false) List<ClaimStatus> status,
            @RequestParam(required = false) Long payerId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return claims.search(status, payerId, patientId, providerId, q, from, to, pageable);
    }

    @GetMapping("/summary")
    public ClaimSummaryResponse summary() {
        return claims.summary();
    }

    @GetMapping("/{id}")
    public ClaimResponse get(@PathVariable Long id) {
        return claims.get(id);
    }

    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(@PathVariable Long id) {
        return claims.history(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<ClaimResponse> create(@Valid @RequestBody ClaimRequest request) {
        ClaimResponse created = claims.create(request);
        return ResponseEntity.created(URI.create("/api/claims/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ClaimResponse update(@PathVariable Long id, @Valid @RequestBody ClaimRequest request) {
        return claims.update(id, request);
    }

    /**
     * Drives the claim through its lifecycle. The state machine decides
     * whether the move is legal; this endpoint never second-guesses it.
     */
    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ClaimResponse transition(@PathVariable Long id,
                                    @Valid @RequestBody TransitionRequest request) {
        return claims.transition(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        claims.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
