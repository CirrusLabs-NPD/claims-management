package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.dto.ReportDtos.ReportPeriod;
import com.claire.claims.dto.ReportDtos.ReportPeriodType;

import java.time.LocalDate;
import java.time.Year;

/**
 * Resolves a requested reporting period into the concrete service-date range it
 * filters on. Kept separate from the service so the boundary maths can be tested
 * without a database.
 *
 * A claim falls in a period when its service dates lie within the range, using the
 * same {@code serviceDateFrom >= from} / {@code serviceDateTo <= to} rule the claims
 * list already applies via {@link ClaimSpecifications}.
 */
public final class ReportPeriods {

    private ReportPeriods() { }

    /** Lowest year we accept, guarding against typos like year=20 or a negative. */
    static final int MIN_YEAR = 2000;

    /**
     * @param type    QUARTER or YEAR (required)
     * @param year    four-digit calendar year (required)
     * @param quarter 1-4, required for QUARTER, must be absent for YEAR
     * @throws BusinessRuleException on any invalid combination (maps to 400 problem+json)
     */
    public static ReportPeriod resolve(ReportPeriodType type, Integer year, Integer quarter) {
        if (type == null) {
            throw new BusinessRuleException("period is required and must be QUARTER or YEAR");
        }
        if (year == null) {
            throw new BusinessRuleException("year is required");
        }
        int maxYear = Year.now().getValue() + 1;
        if (year < MIN_YEAR || year > maxYear) {
            throw new BusinessRuleException(
                    "year must be between " + MIN_YEAR + " and " + maxYear + " (got " + year + ")");
        }

        if (type == ReportPeriodType.YEAR) {
            if (quarter != null) {
                throw new BusinessRuleException(
                        "quarter must not be supplied for a YEAR report; drop it or use period=QUARTER");
            }
            LocalDate from = LocalDate.of(year, 1, 1);
            LocalDate to = LocalDate.of(year, 12, 31);
            return new ReportPeriod(ReportPeriodType.YEAR, year, null, from, to, "FY" + year);
        }

        // QUARTER
        if (quarter == null) {
            throw new BusinessRuleException("quarter (1-4) is required for a QUARTER report");
        }
        if (quarter < 1 || quarter > 4) {
            throw new BusinessRuleException("quarter must be between 1 and 4 (got " + quarter + ")");
        }
        int firstMonth = (quarter - 1) * 3 + 1;
        LocalDate from = LocalDate.of(year, firstMonth, 1);
        LocalDate to = from.plusMonths(3).minusDays(1);
        return new ReportPeriod(ReportPeriodType.QUARTER, year, quarter, from, to,
                "Q" + quarter + " " + year);
    }
}
