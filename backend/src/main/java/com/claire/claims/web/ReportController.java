package com.claire.claims.web;

import com.claire.claims.dto.ReportDtos.ClaimReport;
import com.claire.claims.dto.ReportDtos.ReportPeriodType;
import com.claire.claims.service.ClaimReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Claims reporting. Distinct from the Phase 2 {@code /api/analytics/*} surface:
 * this returns a rolled-up summary plus the underlying claim detail for a chosen
 * period.
 *
 * Read-only, so — like the other GET endpoints — it requires authentication only
 * (any role), gated by SecurityConfig's {@code anyRequest().authenticated()}.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ClaimReportService reports;

    public ReportController(ClaimReportService reports) {
        this.reports = reports;
    }

    /**
     * Claims report for a quarter or a full year.
     *
     * @param period  QUARTER or YEAR (required)
     * @param year    four-digit calendar year (required)
     * @param quarter 1-4, required when {@code period=QUARTER}, rejected when {@code period=YEAR}
     */
    @GetMapping("/claims")
    public ClaimReport claims(@RequestParam ReportPeriodType period,
                              @RequestParam Integer year,
                              @RequestParam(required = false) Integer quarter) {
        return reports.build(period, year, quarter);
    }
}
