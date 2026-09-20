package com.claire.claims.web;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.GlobalExceptionHandler;
import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.dto.ClaimDtos.ClaimListItem;
import com.claire.claims.dto.ClaimDtos.StatusBucket;
import com.claire.claims.dto.ReportDtos.ClaimReport;
import com.claire.claims.dto.ReportDtos.ReportPeriod;
import com.claire.claims.dto.ReportDtos.ReportPeriodType;
import com.claire.claims.dto.ReportDtos.ReportSummary;
import com.claire.claims.dto.ReportDtos.TypeBucket;
import com.claire.claims.service.ClaimReportService;
import com.claire.claims.service.export.ClaimReportExporter;
import com.claire.claims.service.export.ExportFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The reporting endpoint verified at the HTTP boundary — through the real
 * {@link ReportController} and {@link GlobalExceptionHandler} (RFC 9457
 * problem+json), with the service and exporter mocked. This slice proves the WEB
 * contract the service-layer tests cannot reach:
 *
 *  - the QUARTER and YEAR JSON shapes ({ period, summary, detail[] }),
 *  - the export content types and Content-Disposition period-stamped filenames,
 *  - the 400 failure paths returning problem+json rather than a 500, and
 *  - an empty period returning a valid, zeroed 200.
 *
 * Standalone MockMvc is used rather than {@code @WebMvcTest}: Spring Boot 4.1
 * has removed the sliced test autoconfigurers, and a full {@code @SpringBootTest}
 * cannot boot here because the app requires a live Postgres (Flyway +
 * ddl-auto=validate), which the sandbox has none of. Standalone setup exercises
 * the controller, argument binding and the exception handler with no context and
 * no database. RBAC is covered separately in {@link ReportSecurityTest}.
 */
class ReportControllerWebMvcTest {

    private MockMvc mvc;
    private ClaimReportService reports;
    private ClaimReportExporter exporter;

    @BeforeEach
    void setUp() {
        reports = mock(ClaimReportService.class);
        exporter = mock(ClaimReportExporter.class);

        mvc = MockMvcBuilders.standaloneSetup(new ReportController(reports, exporter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ------------------------------------------------------------------
    // Sample reports the mocked service returns.
    // ------------------------------------------------------------------

    private static ClaimReport quarterReport() {
        ReportPeriod period = new ReportPeriod(ReportPeriodType.QUARTER, 2026, 3,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30), "Q3 2026");
        return new ClaimReport(period, sampleSummary(), sampleDetail());
    }

    private static ClaimReport yearReport() {
        ReportPeriod period = new ReportPeriod(ReportPeriodType.YEAR, 2026, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "FY2026");
        return new ClaimReport(period, sampleSummary(), sampleDetail());
    }

    private static ClaimReport emptyReport() {
        ReportPeriod period = new ReportPeriod(ReportPeriodType.QUARTER, 2000, 1,
                LocalDate.of(2000, 1, 1), LocalDate.of(2000, 3, 31), "Q1 2000");
        ReportSummary zeroed = new ReportSummary(0, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, List.of(), List.of());
        return new ClaimReport(period, zeroed, List.of());
    }

    private static ReportSummary sampleSummary() {
        List<StatusBucket> byStatus = List.of(
                new StatusBucket(ClaimStatus.PAID, "Paid in full", 1,
                        new BigDecimal("100.00"), new BigDecimal("100.00")),
                new StatusBucket(ClaimStatus.SUBMITTED, "Sent to the payer, awaiting acknowledgement",
                        1, new BigDecimal("250.50"), new BigDecimal("0.00")));
        List<TypeBucket> byType = List.of(
                new TypeBucket("11", 2, new BigDecimal("350.50"), new BigDecimal("100.00")));
        return new ReportSummary(2, new BigDecimal("350.50"), new BigDecimal("100.00"),
                new BigDecimal("250.50"), byStatus, byType);
    }

    private static List<ClaimListItem> sampleDetail() {
        return List.of(new ClaimListItem(1L, "CLM-2026-000001", ClaimStatus.PAID,
                "Doe, Jane", "MRN-1", "Acme Health", "Dr. Smith",
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 12),
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("0.00"),
                OffsetDateTime.parse("2026-07-13T09:00:00Z"),
                OffsetDateTime.parse("2026-07-10T08:00:00Z")));
    }

    // ------------------------------------------------------------------
    // AC: QUARTER JSON shape
    // ------------------------------------------------------------------

    @Test
    void quarterReturns200WithPeriodSummaryAndDetail() throws Exception {
        when(reports.build(ReportPeriodType.QUARTER, 2026, 3)).thenReturn(quarterReport());

        mvc.perform(get("/api/reports/claims")
                        .param("period", "QUARTER").param("year", "2026").param("quarter", "3"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.period.type").value("QUARTER"))
                .andExpect(jsonPath("$.period.year").value(2026))
                .andExpect(jsonPath("$.period.quarter").value(3))
                .andExpect(jsonPath("$.summary.totalClaims").value(2))
                .andExpect(jsonPath("$.summary.totalCharged").value(350.50))
                .andExpect(jsonPath("$.summary.totalPaid").value(100.00))
                .andExpect(jsonPath("$.summary.outstandingReceivable").value(250.50))
                .andExpect(jsonPath("$.summary.byStatus").isArray())
                .andExpect(jsonPath("$.summary.byStatus[0].status").exists())
                .andExpect(jsonPath("$.summary.byStatus[0].count").exists())
                .andExpect(jsonPath("$.summary.byStatus[0].totalCharge").exists())
                .andExpect(jsonPath("$.summary.byStatus[0].paidAmount").exists())
                .andExpect(jsonPath("$.detail").isArray())
                .andExpect(jsonPath("$.detail[0].claimNumber").value("CLM-2026-000001"))
                .andExpect(jsonPath("$.detail[0].status").value("PAID"))
                .andExpect(jsonPath("$.detail[0].patientName").value("Doe, Jane"))
                .andExpect(jsonPath("$.detail[0].payerName").value("Acme Health"))
                .andExpect(jsonPath("$.detail[0].providerName").value("Dr. Smith"))
                .andExpect(jsonPath("$.detail[0].serviceDateFrom").value("2026-07-10"))
                .andExpect(jsonPath("$.detail[0].serviceDateTo").value("2026-07-12"))
                .andExpect(jsonPath("$.detail[0].totalCharge").value(100.00))
                .andExpect(jsonPath("$.detail[0].paidAmount").value(100.00))
                .andExpect(jsonPath("$.detail[0].outstanding").value(0.00))
                .andExpect(jsonPath("$.detail[0].submittedAt").exists());
    }

    // ------------------------------------------------------------------
    // AC: YEAR JSON shape (no quarter)
    // ------------------------------------------------------------------

    @Test
    void yearWithoutQuarterReturns200WithFullYearRollupAndDetail() throws Exception {
        when(reports.build(ReportPeriodType.YEAR, 2026, null)).thenReturn(yearReport());

        mvc.perform(get("/api/reports/claims")
                        .param("period", "YEAR").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.type").value("YEAR"))
                .andExpect(jsonPath("$.period.year").value(2026))
                .andExpect(jsonPath("$.period.quarter").doesNotExist())
                .andExpect(jsonPath("$.summary.totalClaims").value(2))
                .andExpect(jsonPath("$.detail").isArray());
    }

    // ------------------------------------------------------------------
    // AC: export content types + Content-Disposition filename
    // ------------------------------------------------------------------

    @Test
    void csvExportStreamsTextCsvAsAttachmentWithPeriodFilename() throws Exception {
        when(reports.build(ReportPeriodType.QUARTER, 2026, 3)).thenReturn(quarterReport());
        when(exporter.export(any(), eq(ExportFormat.CSV))).thenReturn("a,b\r\n".getBytes());
        when(exporter.filename(any(), eq(ExportFormat.CSV))).thenReturn("claims-report-Q3-2026.csv");

        mvc.perform(get("/api/reports/claims/export.csv")
                        .param("period", "QUARTER").param("year", "2026").param("quarter", "3"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("attachment"),
                                org.hamcrest.Matchers.containsString("claims-report-Q3-2026.csv"))));
    }

    @Test
    void xlsxExportStreamsSpreadsheetAsAttachmentWithPeriodFilename() throws Exception {
        when(reports.build(ReportPeriodType.QUARTER, 2026, 3)).thenReturn(quarterReport());
        when(exporter.export(any(), eq(ExportFormat.XLSX))).thenReturn(new byte[]{1, 2, 3});
        when(exporter.filename(any(), eq(ExportFormat.XLSX))).thenReturn("claims-report-Q3-2026.xlsx");

        mvc.perform(get("/api/reports/claims/export.xlsx")
                        .param("period", "QUARTER").param("year", "2026").param("quarter", "3"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("claims-report-Q3-2026.xlsx")));
    }

    @Test
    void pdfExportStreamsPdfAsAttachmentWithPeriodFilename() throws Exception {
        when(reports.build(ReportPeriodType.YEAR, 2026, null)).thenReturn(yearReport());
        when(exporter.export(any(), eq(ExportFormat.PDF))).thenReturn("%PDF-1.4".getBytes());
        when(exporter.filename(any(), eq(ExportFormat.PDF))).thenReturn("claims-report-FY2026.pdf");

        mvc.perform(get("/api/reports/claims/export.pdf")
                        .param("period", "YEAR").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("claims-report-FY2026.pdf")));
    }

    // ------------------------------------------------------------------
    // AC: the four 400 failure paths return RFC 9457 problem+json, not 500
    // ------------------------------------------------------------------

    @Test
    void missingYearReturns400ProblemJson() throws Exception {
        // year is a required @RequestParam -> MissingServletRequestParameterException.
        mvc.perform(get("/api/reports/claims")
                        .param("period", "QUARTER").param("quarter", "3"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void quarterOutOfRangeReturns400ProblemJson() throws Exception {
        when(reports.build(ReportPeriodType.QUARTER, 2026, 5))
                .thenThrow(new BusinessRuleException("quarter must be between 1 and 4 (got 5)"));

        mvc.perform(get("/api/reports/claims")
                        .param("period", "QUARTER").param("year", "2026").param("quarter", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("quarter must be between 1 and 4")));
    }

    @Test
    void quarterSuppliedForYearReturns400ProblemJson() throws Exception {
        when(reports.build(ReportPeriodType.YEAR, 2026, 2))
                .thenThrow(new BusinessRuleException("quarter must not be supplied for a YEAR report"));

        mvc.perform(get("/api/reports/claims")
                        .param("period", "YEAR").param("year", "2026").param("quarter", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("quarter must not be supplied")));
    }

    @Test
    void invalidPeriodEnumReturns400ProblemJson() throws Exception {
        // A bogus period value -> MethodArgumentTypeMismatchException -> 400, not 500.
        mvc.perform(get("/api/reports/claims")
                        .param("period", "WEEK").param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    // ------------------------------------------------------------------
    // AC: empty period -> valid empty report with 200
    // ------------------------------------------------------------------

    @Test
    void periodWithNoClaimsReturns200WithZeroedSummaryAndEmptyDetail() throws Exception {
        when(reports.build(ReportPeriodType.QUARTER, 2000, 1)).thenReturn(emptyReport());

        mvc.perform(get("/api/reports/claims")
                        .param("period", "QUARTER").param("year", "2000").param("quarter", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalClaims").value(0))
                .andExpect(jsonPath("$.summary.totalCharged").value(0))
                .andExpect(jsonPath("$.detail").isArray())
                .andExpect(jsonPath("$.detail").isEmpty());
    }
}
