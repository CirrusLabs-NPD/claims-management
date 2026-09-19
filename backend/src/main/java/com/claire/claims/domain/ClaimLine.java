package com.claire.claims.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One billed service line on a claim (a CPT/HCPCS procedure). */
@Entity
@Table(name = "claim_line")
public class ClaimLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "cpt_code", nullable = false, length = 5)
    private String cptCode;

    /** Comma-separated CPT modifiers, e.g. "25,LT". */
    @Column(length = 20)
    private String modifiers;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(nullable = false)
    private Integer units = 1;

    @Column(name = "charge_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal chargeAmount = BigDecimal.ZERO;

    @Column(name = "allowed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal allowedAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(length = 255)
    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Claim getClaim() { return claim; }
    public void setClaim(Claim claim) { this.claim = claim; }
    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    public String getCptCode() { return cptCode; }
    public void setCptCode(String cptCode) { this.cptCode = cptCode; }
    public String getModifiers() { return modifiers; }
    public void setModifiers(String modifiers) { this.modifiers = modifiers; }
    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate serviceDate) { this.serviceDate = serviceDate; }
    public Integer getUnits() { return units; }
    public void setUnits(Integer units) { this.units = units; }
    public BigDecimal getChargeAmount() { return chargeAmount; }
    public void setChargeAmount(BigDecimal chargeAmount) { this.chargeAmount = chargeAmount; }
    public BigDecimal getAllowedAmount() { return allowedAmount; }
    public void setAllowedAmount(BigDecimal allowedAmount) { this.allowedAmount = allowedAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
