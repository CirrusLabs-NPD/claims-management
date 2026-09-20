package com.claire.claims.service;

import com.claire.claims.domain.Claim;
import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.dto.ClaimDtos.ClaimListItem;
import com.claire.claims.dto.ClaimDtos.StatusBucket;
import com.claire.claims.dto.ReportDtos.*;
import com.claire.claims.repository.ClaimRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the claims report for a quarter or a full year: a rolled-up summary
 * (totals and counts, by status and by type) on top of the underlying claim
 * detail rows.
 *
 * Access follows the repository's own read pattern — single-tenant RBAC, so an
 * authenticated user of any role sees all claims. There is no tenant/owner
 * column in this schema, so no such filter is invented here; every query is
 * scoped only by the reporting period, exactly as the claims list scopes by its
 * service-date filters.
 */
@Service
public class ClaimReportService {

    private static final List<ClaimStatus> CLOSED = List.of(ClaimStatus.PAID, ClaimStatus.VOID);

    private final ClaimRepository claims;
    private final ClaimMapper mapper;

    public ClaimReportService(ClaimRepository claims, ClaimMapper mapper) {
        this.claims = claims;
        this.mapper = mapper;
    }

    /**
     * @param type    QUARTER or YEAR
     * @param year    calendar year
     * @param quarter 1-4 for QUARTER, must be null for YEAR
     */
    @Transactional(readOnly = true)
    public ClaimReport build(ReportPeriodType type, Integer year, Integer quarter) {
        ReportPeriod period = ReportPeriods.resolve(type, year, quarter);
        ReportSummary summary = summaryFor(period);
        List<ClaimListItem> detail = detailFor(period);
        return new ClaimReport(period, period.year(), period.quarter(), summary, detail);
    }

    // ---------------------------------------------------------------------

    private ReportSummary summaryFor(ReportPeriod period) {
        List<StatusBucket> byStatus = new ArrayList<>();
        long totalClaims = 0;
        BigDecimal totalCharged = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (Object[] row : claims.summaryByStatusInPeriod(period.from(), period.to())) {
            ClaimStatus status = (ClaimStatus) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal charged = money(row[2]);
            BigDecimal paid = money(row[3]);
            byStatus.add(new StatusBucket(status, status.getDescription(), count, charged, paid));
            totalClaims += count;
            totalCharged = totalCharged.add(charged);
            totalPaid = totalPaid.add(paid);
        }

        // Every status appears, including empty ones, so a client never has to
        // guess which buckets were dropped for having no rows in the period.
        Set<ClaimStatus> present = new HashSet<>();
        byStatus.forEach(b -> present.add(b.status()));
        for (ClaimStatus status : ClaimStatus.values()) {
            if (!present.contains(status)) {
                byStatus.add(new StatusBucket(status, status.getDescription(), 0,
                                              BigDecimal.ZERO, BigDecimal.ZERO));
            }
        }
        byStatus.sort((a, b) -> a.status().compareTo(b.status()));

        List<TypeBucket> byType = new ArrayList<>();
        for (Object[] row : claims.summaryByTypeInPeriod(period.from(), period.to())) {
            String code = row[0] == null ? "" : row[0].toString();
            long count = ((Number) row[1]).longValue();
            byType.add(new TypeBucket(code, count, money(row[2]), money(row[3])));
        }

        BigDecimal outstanding = money(
                claims.outstandingReceivableInPeriod(period.from(), period.to(), CLOSED));

        return new ReportSummary(totalClaims, money(totalCharged), money(totalPaid),
                                 outstanding, byStatus, byType);
    }

    private List<ClaimListItem> detailFor(ReportPeriod period) {
        // Same date rule the claims list uses, expressed through the same
        // composable specifications, so detail and summary always agree.
        Specification<Claim> spec = ClaimSpecifications.allOf(
                ClaimSpecifications.serviceDateFrom(period.from()),
                ClaimSpecifications.serviceDateTo(period.to()));

        Sort sort = Sort.by(Sort.Order.asc("serviceDateFrom"), Sort.Order.asc("claimNumber"));
        return claims.findAll(spec, sort).stream().map(mapper::toListItem).toList();
    }

    /** Coerces whatever numeric type the aggregate returned into scaled money. */
    private static BigDecimal money(Object value) {
        if (value == null) return BigDecimal.ZERO;
        BigDecimal d = (value instanceof BigDecimal bd) ? bd : new BigDecimal(value.toString());
        return d.setScale(2, RoundingMode.HALF_UP);
    }
}
