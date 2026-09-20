package com.claire.claims.web;

import com.claire.claims.common.GlobalExceptionHandler;
import com.claire.claims.dto.ReportDtos.ClaimReport;
import com.claire.claims.dto.ReportDtos.ReportPeriod;
import com.claire.claims.dto.ReportDtos.ReportPeriodType;
import com.claire.claims.dto.ReportDtos.ReportSummary;
import com.claire.claims.service.ClaimReportService;
import com.claire.claims.service.export.ClaimReportExporter;
import com.claire.claims.service.export.ExportFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract of {@code GET /api/reports/claims}, exercised through
 * MockMvc with the real {@link GlobalExceptionHandler} wired in so the failure
 * paths return RFC 9457 problem+json 400s rather than 500s.
 *
 * The service and exporter are mocked — this test is about the web layer:
 * the {@code format} dispatch, the JSON body shape ({@code year}/{@code quarter}
 * at the top level), and the download headers. The rollup and export bytes are
 * proven against real data in {@code ClaimReportServiceTest} and
 * {@code ClaimReportExportE2ETest}.
 */
class ReportControllerTest {

    private ClaimReportService reports;
    private ClaimReportExporter exporter;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reports = mock(ClaimReportService.class);
        exporter = mock(ClaimReportExporter.class);
        mvc = MockMvcBuilders.standaloneSetup(new ReportController(reports, exporter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ClaimReport quarterReport() {
        ReportPeriod period = new ReportPeriod(ReportPeriodType.QUARTER, 2026, 3,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30), "Q3 2026");
        ReportSummary summary = new ReportSummary(2, new BigDecimal("350.50"),
                new BigDecimal("100.00"), new BigDecimal("250.50"), List.of(), List.of());
        return new ClaimReport(period, 2026, 3, summary, List.of());
    }

    @Test
    void jsonIsTheDefaultAndCarriesYearAndQuarterAtTheTopLevel() throws Exception {
        when(reports.build(ReportPeriodType.QUARTER, 2026, 3)).thenReturn(quarterReport());

        mvc.perform(get("/api/reports/claims")
                        .param("period", "QUARTER").param("year", "2026").param("quarter", "3"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.quarter").value(3))
                .andExpect(jsonPath("$.period.type").value("QUARTER"))
                .andExpect(jsonPath("$.summary.totalClaims").value(2))
                .andExpect(jsonPath("$.summary.outstandingReceivable").value(250.50))
                .andExpect(jsonPath("$.detail").isArray());

        verifyNoInteractions(exporter);
    }

    @Test
    void csvFormatStreamsAnAttachmentWithAPeriodStampedFilename() throws Exception {
        ClaimReport report = quarterReport();
        when(reports.build(ReportPeriodType.QUARTER, 2026, 3)).thenReturn(report);
        when(exporter.export(eq(report), eq(ExportFormat.CSV))).thenReturn("csv-bytes".getBytes());
        when(exporter.filename(eq(report), eq(ExportFormat.CSV))).thenReturn("claims-report-2026-Q3.csv");

        mvc.perform(get("/api/reports/claims")
                        .param("period", "QUARTER").param("year", "2026").param("quarter", "3")
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("claims-report-2026-Q3.csv")));
    }

    @Test
    void xlsxFormatUsesTheSpreadsheetContentType() throws Exception {
        ClaimReport report = quarterReport();
        when(reports.build(ReportPeriodType.QUARTER, 2026, 3)).thenReturn(report);
        when(exporter.export(eq(report), eq(ExportFormat.XLSX))).thenReturn(new byte[]{1, 2, 3});
        when(exporter.filename(eq(report), eq(ExportFormat.XLSX))).thenReturn("claims-report-2026-Q3.xlsx");

        mvc.perform(get("/api/reports/claims")
                        .param("period", "QUARTER").param("year", "2026").param("quarter", "3")
                        .param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void unknownFormatIsRejectedWithProblemJson() throws Exception {
        when(reports.build(any(), any(), any())).thenReturn(quarterReport());

        mvc.perform(get("/api/reports/claims")
                        .param("period", "QUARTER").param("year", "2026").param("quarter", "3")
                        .param("format", "xml"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("format must be one of")));
    }

    @Test
    void missingYearIsRejectedWithBadRequest() throws Exception {
        mvc.perform(get("/api/reports/claims").param("period", "QUARTER").param("quarter", "3"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void unparseablePeriodEnumIsRejectedWithBadRequest() throws Exception {
        mvc.perform(get("/api/reports/claims")
                        .param("period", "WEEK").param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
