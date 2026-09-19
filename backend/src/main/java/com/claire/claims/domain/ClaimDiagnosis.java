package com.claire.claims.domain;

import jakarta.persistence.*;

/** An ICD-10-CM diagnosis code attached to a claim. Sequence 1 is principal. */
@Entity
@Table(name = "claim_diagnosis")
public class ClaimDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "icd10_code", nullable = false, length = 10)
    private String icd10Code;

    @Column(length = 255)
    private String description;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Claim getClaim() { return claim; }
    public void setClaim(Claim claim) { this.claim = claim; }
    public String getIcd10Code() { return icd10Code; }
    public void setIcd10Code(String icd10Code) { this.icd10Code = icd10Code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Integer sequenceNo) { this.sequenceNo = sequenceNo; }
}
