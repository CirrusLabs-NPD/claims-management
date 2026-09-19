package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.ClaimDtos.*;
import com.claire.claims.repository.*;
import com.claire.claims.security.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ClaimService {

    private final ClaimRepository claims;
    private final ClaimStatusHistoryRepository history;
    private final PatientRepository patients;
    private final ProviderRepository providers;
    private final PayerRepository payers;
    private final InsurancePolicyRepository policies;
    private final ClaimStatusMachine statusMachine;
    private final ClaimMapper mapper;
    private final CurrentUserProvider currentUser;

    public ClaimService(ClaimRepository claims,
                        ClaimStatusHistoryRepository history,
                        PatientRepository patients,
                        ProviderRepository providers,
                        PayerRepository payers,
                        InsurancePolicyRepository policies,
                        ClaimStatusMachine statusMachine,
                        ClaimMapper mapper,
                        CurrentUserProvider currentUser) {
        this.claims = claims;
        this.history = history;
        this.patients = patients;
        this.providers = providers;
        this.payers = payers;
        this.policies = policies;
        this.statusMachine = statusMachine;
        this.mapper = mapper;
        this.currentUser = currentUser;
    }

    // =====================================================================
    // Queries
    // =====================================================================

    @Transactional(readOnly = true)
    public PageResponse<ClaimListItem> search(List<ClaimStatus> statuses, Long payerId, Long patientId,
                                              Long providerId, String q, LocalDate from, LocalDate to,
                                              Pageable pageable) {
        Specification<Claim> spec = ClaimSpecifications.allOf(
                ClaimSpecifications.hasStatusIn(statuses),
                ClaimSpecifications.hasPayer(payerId),
                ClaimSpecifications.hasPatient(patientId),
                ClaimSpecifications.hasProvider(providerId),
                ClaimSpecifications.serviceDateFrom(from),
                ClaimSpecifications.serviceDateTo(to),
                ClaimSpecifications.matchesText(q));

        Page<Claim> page = claims.findAll(spec, pageable);
        return new PageResponse<>(
                page.getContent().stream().map(mapper::toListItem).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
    }

    @Transactional(readOnly = true)
    public ClaimResponse get(Long id) {
        return mapper.toResponse(loadWithReferences(id));
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> history(Long id) {
        if (!claims.existsById(id)) {
            throw new NotFoundException("Claim", id);
        }
        return history.findByClaimIdOrderByChangedAtAsc(id).stream().map(mapper::toHistory).toList();
    }

    @Transactional(readOnly = true)
    public ClaimSummaryResponse summary() {
        List<StatusBucket> buckets = new ArrayList<>();
        long totalClaims = 0;
        BigDecimal totalCharged = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (Object[] row : claims.summaryByStatus()) {
            ClaimStatus status = (ClaimStatus) row[0];
            long count = ((Number) row[1]).longValue();
            // COALESCE(SUM(...), 0) can come back as any Number depending on
            // how the dialect types the literal, so never cast it blind.
            BigDecimal charged = money(row[2]);
            BigDecimal paid = money(row[3]);

            buckets.add(new StatusBucket(status, status.getDescription(), count, charged, paid));
            totalClaims += count;
            totalCharged = totalCharged.add(charged);
            totalPaid = totalPaid.add(paid);
        }

        // Every status appears, including the empty ones, so the dashboard
        // does not silently drop a bucket when there is nothing in it.
        Set<ClaimStatus> present = new HashSet<>();
        buckets.forEach(b -> present.add(b.status()));
        for (ClaimStatus status : ClaimStatus.values()) {
            if (!present.contains(status)) {
                buckets.add(new StatusBucket(status, status.getDescription(), 0,
                                             BigDecimal.ZERO, BigDecimal.ZERO));
            }
        }
        buckets.sort((a, b) -> a.status().compareTo(b.status()));

        BigDecimal outstanding = money(claims.outstandingReceivable(
                List.of(ClaimStatus.PAID, ClaimStatus.VOID)));

        return new ClaimSummaryResponse(totalClaims, money(totalCharged), money(totalPaid),
                                        outstanding, buckets);
    }

    /** Coerces whatever numeric type the aggregate returned into scaled money. */
    private static BigDecimal money(Object value) {
        if (value == null) return BigDecimal.ZERO;
        BigDecimal d = (value instanceof BigDecimal bd)
                ? bd
                : new BigDecimal(value.toString());
        return d.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // =====================================================================
    // Commands
    // =====================================================================

    @Transactional
    public ClaimResponse create(ClaimRequest request) {
        validateDates(request);

        Claim claim = new Claim();
        claim.setClaimNumber(nextClaimNumber());
        claim.setStatus(ClaimStatus.DRAFT);
        claim.setCreatedBy(currentUser.username());
        applyRequest(claim, request);

        Claim saved = claims.save(claim);
        history.save(new ClaimStatusHistory(saved, null, ClaimStatus.DRAFT,
                                            "Claim created", currentUser.username()));
        return mapper.toResponse(saved);
    }

    @Transactional
    public ClaimResponse update(Long id, ClaimRequest request) {
        Claim claim = loadWithReferences(id);

        // The central editability rule. Anything past DRAFT is an immutable
        // billing record; corrections go through REJECTED -> DRAFT or a void
        // and re-file, exactly as a real clearinghouse would require.
        if (!claim.getStatus().isEditable()) {
            throw new ConflictException(
                    "Claim " + claim.getClaimNumber() + " is " + claim.getStatus()
                    + " and can no longer be edited. Only DRAFT claims are editable.");
        }

        validateDates(request);
        claim.getLines().clear();
        claim.getDiagnoses().clear();
        applyRequest(claim, request);

        return mapper.toResponse(claims.save(claim));
    }

    @Transactional
    public ClaimResponse transition(Long id, TransitionRequest request) {
        Claim claim = loadWithReferences(id);
        ClaimStatusHistory entry = statusMachine.apply(claim, request, currentUser.username());
        history.save(entry);
        return mapper.toResponse(claims.save(claim));
    }

    @Transactional
    public void delete(Long id) {
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim", id));
        if (claim.getStatus() != ClaimStatus.DRAFT) {
            throw new ConflictException(
                    "Only DRAFT claims can be deleted. Claim " + claim.getClaimNumber()
                    + " is " + claim.getStatus() + " - void it instead, which preserves the audit trail.");
        }
        claims.delete(claim);
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private Claim loadWithReferences(Long id) {
        return claims.findWithReferencesById(id)
                .orElseThrow(() -> new NotFoundException("Claim", id));
    }

    private void validateDates(ClaimRequest request) {
        if (request.serviceDateTo().isBefore(request.serviceDateFrom())) {
            throw new BusinessRuleException("serviceDateTo cannot be before serviceDateFrom");
        }
        if (request.serviceDateFrom().isAfter(LocalDate.now())) {
            throw new BusinessRuleException("Service dates cannot be in the future");
        }
        for (ClaimLineRequest line : request.lines()) {
            if (line.serviceDate().isBefore(request.serviceDateFrom())
                    || line.serviceDate().isAfter(request.serviceDateTo())) {
                throw new BusinessRuleException(
                        "Line service date " + line.serviceDate()
                        + " falls outside the claim service period "
                        + request.serviceDateFrom() + " to " + request.serviceDateTo());
            }
        }
    }

    /** Copies a request onto a claim and recomputes the derived total. */
    private void applyRequest(Claim claim, ClaimRequest request) {
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new NotFoundException("Patient", request.patientId()));
        Provider provider = providers.findById(request.providerId())
                .orElseThrow(() -> new NotFoundException("Provider", request.providerId()));
        Payer payer = payers.findById(request.payerId())
                .orElseThrow(() -> new NotFoundException("Payer", request.payerId()));

        claim.setPatient(patient);
        claim.setProvider(provider);
        claim.setPayer(payer);

        if (request.policyId() != null) {
            InsurancePolicy policy = policies.findById(request.policyId())
                    .orElseThrow(() -> new NotFoundException("InsurancePolicy", request.policyId()));
            if (!policy.getPatient().getId().equals(patient.getId())) {
                throw new BusinessRuleException(
                        "Policy " + policy.getId() + " does not belong to patient " + patient.getMrn());
            }
            claim.setPolicy(policy);
        } else {
            claim.setPolicy(null);
        }

        claim.setServiceDateFrom(request.serviceDateFrom());
        claim.setServiceDateTo(request.serviceDateTo());
        claim.setPlaceOfService(request.placeOfService() == null ? "11" : request.placeOfService());
        claim.setNotes(request.notes());

        int lineNo = 1;
        for (ClaimLineRequest lr : request.lines()) {
            ClaimLine line = new ClaimLine();
            line.setLineNumber(lineNo++);
            line.setCptCode(lr.cptCode().toUpperCase());
            line.setModifiers(lr.modifiers());
            line.setServiceDate(lr.serviceDate());
            line.setUnits(lr.units());
            line.setChargeAmount(lr.chargeAmount());
            line.setDescription(lr.description());
            claim.addLine(line);
        }

        Set<Integer> seenSequences = new HashSet<>();
        for (ClaimDiagnosisRequest dr : request.diagnoses()) {
            if (!seenSequences.add(dr.sequenceNo())) {
                throw new BusinessRuleException(
                        "Duplicate diagnosis sequence " + dr.sequenceNo() + " on the claim");
            }
            ClaimDiagnosis dx = new ClaimDiagnosis();
            dx.setIcd10Code(dr.icd10Code().toUpperCase());
            dx.setDescription(dr.description());
            dx.setSequenceNo(dr.sequenceNo());
            claim.addDiagnosis(dx);
        }

        // The invariant: a claim's total is always the sum of its lines.
        // Never taken from the client.
        claim.setTotalCharge(claim.computeTotalCharge());
    }

    /**
     * Mints CLM-{year}-{6-digit sequence}.
     *
     * Derived from the current maximum rather than a database sequence so the
     * numbering stays readable and gap-free per year. Under heavy concurrency
     * this would need a dedicated sequence table; the unique constraint on
     * claim_number makes a collision loud rather than silent.
     */
    private String nextClaimNumber() {
        String year = String.valueOf(Year.now().getValue());
        long next = claims.maxClaimNumberForYear(year)
                .map(max -> {
                    try {
                        return Long.parseLong(max.substring(max.lastIndexOf('-') + 1)) + 1;
                    } catch (NumberFormatException e) {
                        return 1L;
                    }
                })
                .orElse(1L);
        return String.format("CLM-%s-%06d", year, next);
    }
}
