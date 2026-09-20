package com.claire.claims.dto;

import com.claire.claims.dto.ClaimDtos.ClaimListItem;
import com.claire.claims.dto.ClaimDtos.StatusBucket;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs for the claims report: period selection, rolled-up summary and detail rows. */
public final class ReportDtos {

    private ReportDtos() { }

    /** Whether the report covers a single quarter or a full calendar year. */
    public enum ReportPeriodType { QUARTER, YEAR }

    /**
     * The period a report covers, resolved to the service-date range it filters on.
     * {@code quarter} is null for a YEAR report.
     */
    public record ReportPeriod(
            ReportPeriodType type,
            int year,
            Integer quarter,
            LocalDate from,
            LocalDate to,
            String label) { }

    /**
     * One row of the "by type" breakdown, keyed on the CMS place-of-service code
     * (the only categorical dimension a claim carries). {@code code} may be an
     * empty string only in the theoretical case of a null column; it is never null.
     */
    public record TypeBucket(
            String code, long count, BigDecimal totalCharge, BigDecimal paidAmount) { }

    /**
     * The rolled-up top of the report. {@code byStatus} reuses the existing
     * dashboard bucket shape so a client can render it the same way.
     */
    public record ReportSummary(
            long totalClaims,
            BigDecimal totalCharged,
            BigDecimal totalPaid,
            BigDecimal outstandingReceivable,
            List<StatusBucket> byStatus,
            List<TypeBucket> byType) { }

    /**
     * The full report: the period it covers, the summary rollup, and the
     * underlying claim detail rows (same flat shape as the claims list).
     *
     * {@code year} and {@code quarter} are surfaced at the top level (in
     * addition to {@link ReportPeriod}) so a client can read the selection back
     * without unpacking the period; {@code quarter} is null for a YEAR report.
     */
    public record ClaimReport(
            ReportPeriod period,
            int year,
            Integer quarter,
            ReportSummary summary,
            List<ClaimListItem> detail) { }
}
