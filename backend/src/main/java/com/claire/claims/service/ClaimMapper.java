package com.claire.claims.service;

import com.claire.claims.domain.*;
import com.claire.claims.dto.ClaimDtos.*;
import com.claire.claims.dto.ReferenceDtos.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Entity to DTO translation. Kept explicit so the API shape is obvious. */
@Component
public class ClaimMapper {

    public ClaimListItem toListItem(Claim c) {
        return new ClaimListItem(
                c.getId(),
                c.getClaimNumber(),
                c.getStatus(),
                c.getPatient().getDisplayName(),
                c.getPatient().getMrn(),
                c.getPayer().getName(),
                c.getProvider().getDisplayName(),
                c.getServiceDateFrom(),
                c.getServiceDateTo(),
                c.getTotalCharge(),
                c.getPaidAmount(),
                c.getOutstandingBalance(),
                c.getSubmittedAt(),
                c.getCreatedAt());
    }

    public ClaimResponse toResponse(Claim c) {
        return new ClaimResponse(
                c.getId(),
                c.getClaimNumber(),
                c.getStatus(),
                c.getStatus().getDescription(),
                c.getPatient().getId(),
                c.getPatient().getDisplayName(),
                c.getPatient().getMrn(),
                c.getProvider().getId(),
                c.getProvider().getDisplayName(),
                c.getProvider().getNpi(),
                c.getPayer().getId(),
                c.getPayer().getName(),
                c.getPolicy() != null ? c.getPolicy().getId() : null,
                c.getPolicy() != null ? c.getPolicy().getMemberId() : null,
                c.getServiceDateFrom(),
                c.getServiceDateTo(),
                c.getPlaceOfService(),
                c.getTotalCharge(),
                c.getAllowedAmount(),
                c.getPaidAmount(),
                c.getPatientResponsibility(),
                c.getOutstandingBalance(),
                c.getNotes(),
                c.getSubmittedAt(),
                c.getCreatedBy(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getStatus().isEditable(),
                List.copyOf(c.getStatus().allowedNext()),
                c.getLines().stream().map(this::toLine).toList(),
                c.getDiagnoses().stream().map(this::toDiagnosis).toList());
    }

    public ClaimLineResponse toLine(ClaimLine l) {
        return new ClaimLineResponse(l.getId(), l.getLineNumber(), l.getCptCode(), l.getModifiers(),
                l.getServiceDate(), l.getUnits(), l.getChargeAmount(),
                l.getAllowedAmount(), l.getPaidAmount(), l.getDescription());
    }

    public ClaimDiagnosisResponse toDiagnosis(ClaimDiagnosis d) {
        return new ClaimDiagnosisResponse(d.getId(), d.getIcd10Code(), d.getDescription(), d.getSequenceNo());
    }

    public StatusHistoryResponse toHistory(ClaimStatusHistory h) {
        return new StatusHistoryResponse(h.getId(), h.getFromStatus(), h.getToStatus(),
                h.getReason(), h.getChangedBy(), h.getChangedAt());
    }

    // ----- reference data -------------------------------------------------

    public PatientResponse toPatient(Patient p, List<InsurancePolicy> policies) {
        return new PatientResponse(p.getId(), p.getMrn(), p.getFirstName(), p.getLastName(),
                p.getDisplayName(), p.getDateOfBirth(), p.getPhone(), p.getEmail(),
                p.getAddressLine1(), p.getCity(), p.getState(), p.getPostalCode(),
                policies == null ? List.of() : policies.stream().map(this::toPolicy).toList());
    }

    public PatientSummary toPatientSummary(Patient p) {
        return new PatientSummary(p.getId(), p.getMrn(), p.getDisplayName(), p.getDateOfBirth());
    }

    public PolicyResponse toPolicy(InsurancePolicy pol) {
        return new PolicyResponse(pol.getId(), pol.getPatient().getId(), pol.getPayer().getId(),
                pol.getPayer().getName(), pol.getMemberId(), pol.getGroupNumber(), pol.getPriority(),
                pol.getEffectiveDate(), pol.getTerminationDate(), pol.isActiveOn(LocalDate.now()));
    }

    public PayerResponse toPayer(Payer p) {
        return new PayerResponse(p.getId(), p.getPayerCode(), p.getName(), p.getPlanType(),
                p.getClaimsAddress(), p.getPhone(), p.isActive());
    }

    public ProviderResponse toProvider(Provider p) {
        return new ProviderResponse(p.getId(), p.getNpi(), p.getFirstName(), p.getLastName(),
                p.getDisplayName(), p.getSpecialty(), p.getTaxId(), p.isActive());
    }
}
