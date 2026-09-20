package com.claire.claims.service.export;

import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.dto.ClaimDtos.ClaimListItem;
import com.claire.claims.dto.ClaimDtos.StatusBucket;
import com.claire.claims.dto.ReportDtos.ClaimReport;
import com.claire.claims.dto.ReportDtos.ReportPeriod;
import com.claire.claims.dto.ReportDtos.ReportPeriodType;
import com.claire.claims.dto.ReportDtos.ReportSummary;
import com.claire.claims.dto.ReportDtos.TypeBucket;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three exporters, exercised without a Spring context or a database: a
 * hand-built {@link ClaimReport} is serialised and the bytes are read back.
 * These pin the download contract — content, structure and period-stamped
 * filename — for CSV, Excel and PDF.
 */
class ClaimReportExporterTest {

    private final ClaimReportExporter exporter = new ClaimReportExporter();

    // A report with a comma and a quote in a free-text field, so CSV escaping is
    // actually tested rather than assumed.
    private ClaimReport sampleReport() {
        ReportPeriod period = new ReportPeriod(
                ReportPeriodType.QUARTER, 2025, 1,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31), "Q1 2025");

        List<StatusBucket> byStatus = List.of(
                new StatusBucket(ClaimStatus.PAID, "Paid in full", 1,
                        new BigDecimal("100.00"), new BigDecimal("100.00")),
                new StatusBucket(ClaimStatus.SUBMITTED, "Sent to the payer, awaiting acknowledgement",
                        1, new BigDecimal("250.50"), new BigDecimal("0.00")));

        List<TypeBucket> byType = List.of(
                new TypeBucket("11", 1, new BigDecimal("100.00"), new BigDecimal("100.00")),
                new TypeBucket("21", 1, new BigDecimal("250.50"), new BigDecimal("0.00")));

        ReportSummary summary = new ReportSummary(
                2, new BigDecimal("350.50"), new BigDecimal("100.00"),
                new BigDecimal("250.50"), byStatus, byType);

        List<ClaimListItem> detail = List.of(
                new ClaimListItem(1L, "CLM-2025-000001", ClaimStatus.PAID,
                        "Doe, Jane", "MRN-1", "Acme Health, Inc.", "Dr. Smith",
                        LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 12),
                        new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("0.00"),
                        OffsetDateTime.parse("2025-01-13T09:00:00Z"),
                        OffsetDateTime.parse("2025-01-10T08:00:00Z")),
                new ClaimListItem(2L, "CLM-2025-000002", ClaimStatus.SUBMITTED,
                        "Roe, \"Rick\"", "MRN-2", "Beta Insurance", "Dr. Jones",
                        LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 3),
                        new BigDecimal("250.50"), new BigDecimal("0.00"), new BigDecimal("250.50"),
                        null, OffsetDateTime.parse("2025-02-01T08:00:00Z")));

        return new ClaimReport(period, 2025, 1, summary, detail);
    }

    @Test
    void filenameIncludesThePeriodLabelAndExtension() {
        ClaimReport report = sampleReport();
        assertThat(exporter.filename(report, ExportFormat.CSV)).isEqualTo("claims-report-2025-Q1.csv");
        assertThat(exporter.filename(report, ExportFormat.XLSX)).isEqualTo("claims-report-2025-Q1.xlsx");
        assertThat(exporter.filename(report, ExportFormat.PDF)).isEqualTo("claims-report-2025-Q1.pdf");
    }

    @Test
    void annualFilenameUsesTheFiscalYearLabel() {
        ReportPeriod period = new ReportPeriod(ReportPeriodType.YEAR, 2024, null,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), "FY2024");
        ClaimReport report = new ClaimReport(period, 2024, null,
                new ReportSummary(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        List.of(), List.of()),
                List.of());
        assertThat(exporter.filename(report, ExportFormat.PDF)).isEqualTo("claims-report-2024.pdf");
    }

    @Test
    void csvCarriesSummaryAndDetailAndEscapesSpecialCharacters() {
        String csv = new String(exporter.export(sampleReport(), ExportFormat.CSV), StandardCharsets.UTF_8);

        // Summary block.
        assertThat(csv).contains("Claims Report,Q1 2025");
        assertThat(csv).contains("Total Claims,2");
        assertThat(csv).contains("Total Charged,350.50");
        assertThat(csv).contains("Outstanding Receivable,250.50");
        assertThat(csv).contains("PAID,Paid in full,1,100.00,100.00");

        // Detail block header and both rows.
        assertThat(csv).contains("Claim Number,Status,Patient");
        assertThat(csv).contains("CLM-2025-000001");
        assertThat(csv).contains("CLM-2025-000002");

        // RFC 4180 escaping: a comma is quoted, a quote is doubled and quoted.
        assertThat(csv).contains("\"Acme Health, Inc.\"");
        assertThat(csv).contains("\"Roe, \"\"Rick\"\"\"");

        // Windows line endings throughout.
        assertThat(csv).contains("\r\n");
    }

    @Test
    void xlsxHasSummaryAndDetailSheetsWithTheDetailRows() throws Exception {
        byte[] bytes = exporter.export(sampleReport(), ExportFormat.XLSX);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getSheet("Summary")).isNotNull();
            Sheet detail = wb.getSheet("Detail");
            assertThat(detail).isNotNull();

            // Header row + one row per detail claim.
            assertThat(detail.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Claim Number");
            assertThat(detail.getLastRowNum()).isEqualTo(2); // rows 1 and 2
            assertThat(detail.getRow(1).getCell(0).getStringCellValue()).isEqualTo("CLM-2025-000001");
            assertThat(detail.getRow(1).getCell(8).getNumericCellValue()).isEqualTo(100.00d);
            assertThat(detail.getRow(2).getCell(0).getStringCellValue()).isEqualTo("CLM-2025-000002");
        }
    }

    @Test
    void pdfStartsWithThePdfMagicNumberAndHasContent() {
        byte[] bytes = exporter.export(sampleReport(), ExportFormat.PDF);

        assertThat(bytes).isNotEmpty();
        // "%PDF" header.
        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        // A non-trivial document, not an empty shell.
        assertThat(bytes.length).isGreaterThan(500);
    }

    @Test
    void exportDispatchMatchesTheFormat() {
        ClaimReport report = sampleReport();
        assertThat(new String(exporter.export(report, ExportFormat.CSV), 0, 4, StandardCharsets.US_ASCII))
                .doesNotStartWith("%PDF");
        assertThat(new String(exporter.export(report, ExportFormat.PDF), 0, 4, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");
    }
}
