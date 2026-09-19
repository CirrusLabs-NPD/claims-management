package com.claire.claims.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Append-only audit trail of every status change.
 * Nothing in the application ever updates or deletes a row here.
 */
@Entity
@Table(name = "claim_status_history")
public class ClaimStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ClaimStatus toStatus;

    @Column(length = 500)
    private String reason;

    @Column(name = "changed_by", nullable = false, length = 60)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt = OffsetDateTime.now();

    public ClaimStatusHistory() { }

    public ClaimStatusHistory(Claim claim, ClaimStatus from, ClaimStatus to,
                              String reason, String changedBy) {
        this.claim = claim;
        this.fromStatus = from;
        this.toStatus = to;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Claim getClaim() { return claim; }
    public void setClaim(Claim claim) { this.claim = claim; }
    public ClaimStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(ClaimStatus fromStatus) { this.fromStatus = fromStatus; }
    public ClaimStatus getToStatus() { return toStatus; }
    public void setToStatus(ClaimStatus toStatus) { this.toStatus = toStatus; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public OffsetDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(OffsetDateTime changedAt) { this.changedAt = changedAt; }
}
