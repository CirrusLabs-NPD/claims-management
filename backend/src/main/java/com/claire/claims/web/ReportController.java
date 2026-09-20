package com.claire.claims.web;

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

    /** The same report as {@link #claims}, streamed as a CSV download. */
    @GetMapping("/claims/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam ReportPeriodType period,
                                            @RequestParam Integer year,
                                            @RequestParam(required = false) Integer quarter) {
        return export(period, year, quarter, ExportFormat.CSV);
    }

    /** The same report, streamed as an Excel (.xlsx) download. */
    @GetMapping("/claims/export.xlsx")
    public ResponseEntity<byte[]> exportXlsx(@RequestParam ReportPeriodType period,
                                             @RequestParam Integer year,
                                             @RequestParam(required = false) Integer quarter) {
        return export(period, year, quarter, ExportFormat.XLSX);
    }

    /** The same report, streamed as a PDF download. */
    @GetMapping("/claims/export.pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestParam ReportPeriodType period,
                                            @RequestParam Integer year,
                                            @RequestParam(required = false) Integer quarter) {
        return export(period, year, quarter, ExportFormat.PDF);
    }

    /**
     * Builds the report through the same tenancy/period-scoped path the JSON
     * endpoint uses, serialises it in {@code format}, and returns it as an
     * attachment with the correct content type and a period-stamped filename.
     */
    private ResponseEntity<byte[]> export(ReportPeriodType period, Integer year,
                                          Integer quarter, ExportFormat format) {
        ClaimReport report = reports.build(period, year, quarter);
        byte[] body = exporter.export(report, format);
        String filename = exporter.filename(report, format);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(format.contentType()));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
