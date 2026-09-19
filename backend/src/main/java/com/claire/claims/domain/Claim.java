package com.claire.claims.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "claim")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", nullable = false, unique = true, length = 24)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private Payer payer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private InsurancePolicy policy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status = ClaimStatus.DRAFT;

    @Column(name = "service_date_from", nullable = false)
    private LocalDate serviceDateFrom;

    @Column(name = "service_date_to", nullable = false)
    private LocalDate serviceDateTo;

    /** CMS place-of-service code. 11 = office, 21 = inpatient hospital, etc. */
    @Column(name = "place_of_service", nullable = false, length = 2)
    private String placeOfService = "11";

    @Column(name = "total_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCharge = BigDecimal.ZERO;

    @Column(name = "allowed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal allowedAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "patient_responsibility", nullable = false, precision = 12, scale = 2)
    private BigDecimal patientResponsibility = BigDecimal.ZERO;

    @Column(length = 1000)
    private String notes;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "created_by", nullable = false, length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    /** Optimistic locking. Two billers editing the same draft is a real scenario. */
    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<ClaimLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<ClaimDiagnosis> diagnoses = new ArrayList<>();

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    // ----- domain helpers -------------------------------------------------

    public void addLine(ClaimLine line) {
        line.setClaim(this);
        this.lines.add(line);
    }

    public void addDiagnosis(ClaimDiagnosis dx) {
        dx.setClaim(this);
        this.diagnoses.add(dx);
    }

    /** Sum of line charges. The claim total must always equal this. */
    public BigDecimal computeTotalCharge() {
        return lines.stream()
                .map(ClaimLine::getChargeAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Outstanding balance for AR reporting. */
    @Transient
    public BigDecimal getOutstandingBalance() {
        BigDecimal basis = allowedAmount.signum() > 0 ? allowedAmount : totalCharge;
        return basis.subtract(paidAmount).max(BigDecimal.ZERO);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClaimNumber() { return claimNumber; }
    public void setClaimNumber(String claimNumber) { this.claimNumber = claimNumber; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public Payer getPayer() { return payer; }
    public void setPayer(Payer payer) { this.payer = payer; }
    public InsurancePolicy getPolicy() { return policy; }
    public void setPolicy(InsurancePolicy policy) { this.policy = policy; }
    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) { this.status = status; }
    public LocalDate getServiceDateFrom() { return serviceDateFrom; }
    public void setServiceDateFrom(LocalDate serviceDateFrom) { this.serviceDateFrom = serviceDateFrom; }
    public LocalDate getServiceDateTo() { return serviceDateTo; }
    public void setServiceDateTo(LocalDate serviceDateTo) { this.serviceDateTo = serviceDateTo; }
    public String getPlaceOfService() { return placeOfService; }
    public void setPlaceOfService(String placeOfService) { this.placeOfService = placeOfService; }
    public BigDecimal getTotalCharge() { return totalCharge; }
    public void setTotalCharge(BigDecimal totalCharge) { this.totalCharge = totalCharge; }
    public BigDecimal getAllowedAmount() { return allowedAmount; }
    public void setAllowedAmount(BigDecimal allowedAmount) { this.allowedAmount = allowedAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getPatientResponsibility() { return patientResponsibility; }
    public void setPatientResponsibility(BigDecimal v) { this.patientResponsibility = v; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public List<ClaimLine> getLines() { return lines; }
    public void setLines(List<ClaimLine> lines) { this.lines = lines; }
    public List<ClaimDiagnosis> getDiagnoses() { return diagnoses; }
    public void setDiagnoses(List<ClaimDiagnosis> diagnoses) { this.diagnoses = diagnoses; }
}
