package com.claire.claims.dto;

import com.claire.claims.domain.PlanType;
import com.claire.claims.domain.PolicyPriority;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public final class ReferenceDtos {

    private ReferenceDtos() { }

    // ----- Patient --------------------------------------------------------

    public record PatientRequest(
            @NotBlank @Size(max = 30) String mrn,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotNull @PastOrPresent(message = "date of birth cannot be in the future")
            LocalDate dateOfBirth,
            @Size(max = 30) String phone,
            @Email @Size(max = 160) String email,
            @Size(max = 160) String addressLine1,
            @Size(max = 80) String city,
            @Pattern(regexp = "^[A-Z]{2}$", message = "state must be a 2-letter code")
            String state,
            @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "invalid ZIP code")
            String postalCode) { }

    public record PatientResponse(
            Long id, String mrn, String firstName, String lastName, String displayName,
            LocalDate dateOfBirth, String phone, String email,
            String addressLine1, String city, String state, String postalCode,
            List<PolicyResponse> policies) { }

    public record PatientSummary(Long id, String mrn, String displayName, LocalDate dateOfBirth) { }

    // ----- Payer ----------------------------------------------------------

    public record PayerRequest(
            @NotBlank @Size(max = 20) String payerCode,
            @NotBlank @Size(max = 160) String name,
            @NotNull PlanType planType,
            @Size(max = 255) String claimsAddress,
            @Size(max = 30) String phone,
            Boolean active) { }

    public record PayerResponse(
            Long id, String payerCode, String name, PlanType planType,
            String claimsAddress, String phone, boolean active) { }

    // ----- Provider -------------------------------------------------------

    public record ProviderRequest(
            @NotBlank @Pattern(regexp = "^\\d{10}$", message = "NPI must be exactly 10 digits")
            String npi,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @Size(max = 80) String specialty,
            @Size(max = 20) String taxId,
            Boolean active) { }

    public record ProviderResponse(
            Long id, String npi, String firstName, String lastName, String displayName,
            String specialty, String taxId, boolean active) { }

    // ----- Insurance policy -----------------------------------------------

    public record PolicyRequest(
            @NotNull Long patientId,
            @NotNull Long payerId,
            @NotBlank @Size(max = 50) String memberId,
            @Size(max = 50) String groupNumber,
            @NotNull PolicyPriority priority,
            @NotNull LocalDate effectiveDate,
            LocalDate terminationDate) { }

    public record PolicyResponse(
            Long id, Long patientId, Long payerId, String payerName,
            String memberId, String groupNumber, PolicyPriority priority,
            LocalDate effectiveDate, LocalDate terminationDate, boolean activeToday) { }
}
