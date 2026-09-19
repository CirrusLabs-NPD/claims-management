package com.claire.claims.dto;

import com.claire.claims.domain.ClaimStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class ClaimDtos {

    private ClaimDtos() { }

    // ----- write models ---------------------------------------------------

    public record ClaimLineRequest(
            @NotBlank
            @Pattern(regexp = "^[0-9A-Z][0-9]{3}[0-9A-Z]$",
                     message = "CPT/HCPCS code must be 5 characters, e.g. 99213 or J1885")
            String cptCode,
            @Pattern(regexp = "^[A-Z0-9]{2}(,[A-Z0-9]{2}){0,3}$",
                     message = "modifiers must be 2-character codes, comma separated")
            String modifiers,
            @NotNull LocalDate serviceDate,
            @NotNull @Min(value = 1, message = "units must be at least 1") Integer units,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2)
            BigDecimal chargeAmount,
            @Size(max = 255) String description) { }

    public record ClaimDiagnosisRequest(
            @NotBlank
            @Pattern(regexp = "^[A-TV-Z][0-9][0-9AB](\\.[0-9A-Z]{1,4})?$",
                     message = "must be a valid ICD-10-CM code, e.g. E11.9")
            String icd10Code,
            @Size(max = 255) String description,
            @NotNull @Min(1) @Max(12) Integer sequenceNo) { }

    public record ClaimRequest(
            @NotNull Long patientId,
            @NotNull Long providerId,
            @NotNull Long payerId,
            Long policyId,
            @NotNull LocalDate serviceDateFrom,
            @NotNull LocalDate serviceDateTo,
            @Pattern(regexp = "^[0-9]{2}$", message = "place of service must be a 2-digit CMS code")
            String placeOfService,
            @Size(max = 1000) String notes,
            @NotEmpty(message = "a claim needs at least one service line")
            @Valid List<ClaimLineRequest> lines,
            @NotEmpty(message = "a claim needs at least one diagnosis")
            @Valid List<ClaimDiagnosisRequest> diagnoses) { }

    public record TransitionRequest(
            @NotNull(message = "targetStatus is required") ClaimStatus targetStatus,
            @Size(max = 500) String reason,
            @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal paidAmount,
            @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal allowedAmount) { }

    // ----- read models ----------------------------------------------------

    public record ClaimLineResponse(
            Long id, Integer lineNumber, String cptCode, String modifiers,
            LocalDate serviceDate, Integer units, BigDecimal chargeAmount,
            BigDecimal allowedAmount, BigDecimal paidAmount, String description) { }

    public record ClaimDiagnosisResponse(
            Long id, String icd10Code, String description, Integer sequenceNo) { }

    public record StatusHistoryResponse(
            Long id, ClaimStatus fromStatus, ClaimStatus toStatus,
            String reason, String changedBy, OffsetDateTime changedAt) { }

    /** Row shape for the claims table. Deliberately flat and light. */
    public record ClaimListItem(
            Long id, String claimNumber, ClaimStatus status,
            String patientName, String patientMrn, String payerName, String providerName,
            LocalDate serviceDateFrom, LocalDate serviceDateTo,
            BigDecimal totalCharge, BigDecimal paidAmount, BigDecimal outstanding,
            OffsetDateTime submittedAt, OffsetDateTime createdAt) { }

    public record ClaimResponse(
            Long id, String claimNumber, ClaimStatus status, String statusDescription,
            Long patientId, String patientName, String patientMrn,
            Long providerId, String providerName, String providerNpi,
            Long payerId, String payerName,
            Long policyId, String policyMemberId,
            LocalDate serviceDateFrom, LocalDate serviceDateTo, String placeOfService,
            BigDecimal totalCharge, BigDecimal allowedAmount, BigDecimal paidAmount,
            BigDecimal patientResponsibility, BigDecimal outstanding,
            String notes, OffsetDateTime submittedAt, String createdBy,
            OffsetDateTime createdAt, OffsetDateTime updatedAt,
            boolean editable,
            List<ClaimStatus> allowedTransitions,
            List<ClaimLineResponse> lines,
            List<ClaimDiagnosisResponse> diagnoses) { }

    public record StatusBucket(
            ClaimStatus status, String description, long count,
            BigDecimal totalCharge, BigDecimal paidAmount) { }

    public record ClaimSummaryResponse(
            long totalClaims,
            BigDecimal totalCharged,
            BigDecimal totalPaid,
            BigDecimal outstandingReceivable,
            List<StatusBucket> byStatus) { }

    public record PageResponse<T>(
            List<T> content, int page, int size,
            long totalElements, int totalPages, boolean last) { }

    /** The state machine, published as data so the UI never hardcodes it. */
    public record StatusTransitionMap(
            Map<String, List<String>> transitions,
            Map<String, String> descriptions,
            List<String> terminal) { }
}
