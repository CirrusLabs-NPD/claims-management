package com.claire.claims.web;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.dto.ReportDtos.ClaimReport;
import com.claire.claims.dto.ReportDtos.ReportPeriodType;
import com.claire.claims.service.ClaimReportService;
import com.claire.claims.service.export.ClaimReportExporter;
import com.claire.claims.service.export.ExportFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Claims reporting. Distinct from the Phase 2 {@code /api/analytics/*} surface:
 * this returns a rolled-up summary plus the underlying claim detail for a chosen
 * period, both as JSON and as a downloadable CSV, Excel or PDF export.
 *
 * Read-only, so — like the other GET endpoints — it requires authentication only
 * (any role), gated by SecurityConfig's {@code anyRequest().authenticated()}.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ClaimReportService reports;
    private final ClaimReportExporter exporter;

    public ReportController(ClaimReportService reports, ClaimReportExporter exporter) {
        this.reports = reports;
        this.exporter = exporter;
    }

    /**
     * Claims report for a quarter or a full year.
     *
     * <p>{@code format=json} (the default) returns
     * {@code { period, year, quarter, summary, detail[] }}. {@code format=csv|xlsx|pdf}
     * streams the same report as a downloadable file with a
     * {@code Content-Disposition: attachment} header and a period-stamped filename.
     * Any other {@code format} value is rejected with 400 problem+json.
     *
     * @param period  QUARTER or YEAR (required)
     * @param year    four-digit calendar year (required)
     * @param quarter 1-4, required when {@code period=QUARTER}, rejected when {@code period=YEAR}
     * @param format  json (default) | csv | xlsx | pdf
     */
    @GetMapping("/claims")
    public ResponseEntity<?> claims(@RequestParam ReportPeriodType period,
                                    @RequestParam Integer year,
                                    @RequestParam(required = false) Integer quarter,
                                    @RequestParam(required = false, defaultValue = "json") String format) {
        ClaimReport report = reports.build(period, year, quarter);

        if (format.equalsIgnoreCase("json")) {
            return ResponseEntity.ok(report);
        }

        ExportFormat exportFormat = parseFormat(format);
        byte[] body = exporter.export(report, exportFormat);
        String filename = exporter.filename(report, exportFormat);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(exportFormat.contentType()));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }

    /**
     * Maps a {@code format} query value to an {@link ExportFormat}, rejecting an
     * unknown one with a 400 problem+json rather than letting it fall through to
     * a 500 or a wrong content type.
     */
    private static ExportFormat parseFormat(String format) {
        try {
            return ExportFormat.valueOf(format.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(
                    "format must be one of json, csv, xlsx, pdf (got '" + format + "')");
        }
    }
}
