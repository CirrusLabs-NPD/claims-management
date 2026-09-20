package com.claire.claims.service.export;

import com.claire.claims.domain.Claim;
import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.domain.Patient;
import com.claire.claims.domain.Payer;
import com.claire.claims.domain.PlanType;
import com.claire.claims.domain.Provider;
import com.claire.claims.dto.ClaimDtos.ClaimListItem;
import com.claire.claims.dto.ReportDtos.ClaimReport;
import com.claire.claims.dto.ReportDtos.ReportPeriodType;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.service.ClaimMapper;
import com.claire.claims.service.ClaimReportService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification of the three export formats over the SAME data path
 * the JSON report uses: a report is built through the real
 * {@link ClaimReportService} (real {@link ClaimRepository} on H2 in PostgreSQL
 * mode, real aggregation SQL), then exported and read back from bytes. This is
 * distinct from {@link ClaimReportExporterTest}, which serialises a hand-built
 * DTO — here the numbers under test come from the database, so the checks prove
 * an export can never disagree with the report API.
 *
 * Closes the S-4 acceptance criteria that need data:
 *  - CSV parses and its summary/detail totals equal the report API totals;
 *  - the .xlsx opens and its Summary/Detail cells equal the report API totals;
 *  - the PDF is valid (%PDF) and its extracted text contains the summary totals;
 *  - filenames encode the period (quarter and annual);
 *  - a claim outside the requested period ("across the boundary") appears in
 *    NONE of the three exports — the period is the only row filter and it is
 *    applied identically to summary, detail and every export.
 *
 * The JPA bootstrap mirrors {@code ClaimReportServiceTest}: a RESOURCE_LOCAL
 * persistence unit and the real repository via Spring Data's
 * {@link JpaRepositoryFactory}, with no Spring context and no live Postgres.
 */
class ClaimReportExportE2ETest {

    private static EntityManagerFactory emf;

    private EntityManager em;
    private ClaimReportService reports;
    private final ClaimReportExporter exporter = new ClaimReportExporter();

    private Patient patient;
    private Provider provider;
    private Payer payer;

    @BeforeAll
    static void bootJpa() {
        emf = Persistence.createEntityManagerFactory("reportslice", Map.of(
                "jakarta.persistence.jdbc.url",
                "jdbc:h2:mem:reportexport;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "jakarta.persistence.jdbc.user", "sa",
                "jakarta.persistence.jdbc.password", "",
                "jakarta.persistence.jdbc.driver", "org.h2.Driver",
                "hibernate.hbm2ddl.auto", "create-drop",
                "hibernate.dialect", "org.hibernate.dialect.H2Dialect"));
    }

    @AfterAll
    static void closeJpa() {
        if (emf != null) emf.close();
    }

    @BeforeEach
    void setUp() {
        em = emf.createEntityManager();
        ClaimRepository claims = new JpaRepositoryFactory(em).getRepository(ClaimRepository.class);
        reports = new ClaimReportService(claims, new ClaimMapper());

        inTx(() -> {
            em.createQuery("DELETE FROM Claim").executeUpdate();
            em.createQuery("DELETE FROM Patient").executeUpdate();
            em.createQuery("DELETE FROM Provider").executeUpdate();
            em.createQuery("DELETE FROM Payer").executeUpdate();
        });
        inTx(() -> {
            patient = persistPatient();
            provider = persistProvider();
            payer = persistPayer();
        });
    }

    // ------------------------------------------------------------------
    // Exported totals == report API totals — CSV
    // ------------------------------------------------------------------

    @Test
    void csvTotalsEqualTheReportApiTotalsAndParseBackToTheDetailRows() {
        seedQ1_2025();
        ClaimReport report = reports.build(ReportPeriodType.QUARTER, 2025, 1);

        String csv = new String(exporter.export(report, ExportFormat.CSV), StandardCharsets.UTF_8);
        List<String[]> rows = parseCsv(csv);

        // The summary block carries exactly the report API's totals.
        assertThat(cellValue(rows, "Total Claims")).isEqualTo(String.valueOf(report.summary().totalClaims()));
        assertThat(cellValue(rows, "Total Charged")).isEqualTo(money(report.summary().totalCharged()));
        assertThat(cellValue(rows, "Total Paid")).isEqualTo(money(report.summary().totalPaid()));
        assertThat(cellValue(rows, "Outstanding Receivable"))
                .isEqualTo(money(report.summary().outstandingReceivable()));

        // The detail block parses back to exactly the report's detail rows, and
        // the parsed charge column sums to the summary total.
        List<String[]> detailRows = detailRowsOf(rows);
        assertThat(detailRows).hasSize(report.detail().size());
        assertThat(detailRows).extracting(r -> r[0])
                .containsExactlyElementsOf(report.detail().stream().map(ClaimListItem::claimNumber).toList());

        BigDecimal parsedChargeSum = detailRows.stream()
                .map(r -> new BigDecimal(r[8]))          // "Total Charge" column
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(parsedChargeSum).isEqualByComparingTo(report.summary().totalCharged());
    }

    // ------------------------------------------------------------------
    // Exported totals == report API totals — Excel
    // ------------------------------------------------------------------

    @Test
    void xlsxOpensAndItsCellsEqualTheReportApiTotals() throws Exception {
        seedQ1_2025();
        ClaimReport report = reports.build(ReportPeriodType.QUARTER, 2025, 1);

        byte[] bytes = exporter.export(report, ExportFormat.XLSX);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet summary = wb.getSheet("Summary");
            Sheet detail = wb.getSheet("Detail");
            assertThat(summary).isNotNull();
            assertThat(detail).isNotNull();

            // Totals row sits directly under the "Total Claims" header row.
            Row header = findRow(summary, "Total Claims");
            assertThat(header).as("Total Claims header row").isNotNull();
            Row totals = summary.getRow(header.getRowNum() + 1);
            assertThat((long) totals.getCell(0).getNumericCellValue())
                    .isEqualTo(report.summary().totalClaims());
            assertThat(BigDecimal.valueOf(totals.getCell(1).getNumericCellValue()))
                    .isEqualByComparingTo(report.summary().totalCharged());
            assertThat(BigDecimal.valueOf(totals.getCell(2).getNumericCellValue()))
                    .isEqualByComparingTo(report.summary().totalPaid());
            assertThat(BigDecimal.valueOf(totals.getCell(3).getNumericCellValue()))
                    .isEqualByComparingTo(report.summary().outstandingReceivable());

            // Detail sheet: one row per claim, and the charge column sums to the total.
            assertThat(detail.getLastRowNum()).isEqualTo(report.detail().size()); // header + N
            BigDecimal cellChargeSum = BigDecimal.ZERO;
            for (int r = 1; r <= detail.getLastRowNum(); r++) {
                cellChargeSum = cellChargeSum.add(
                        BigDecimal.valueOf(detail.getRow(r).getCell(8).getNumericCellValue()));
            }
            assertThat(cellChargeSum).isEqualByComparingTo(report.summary().totalCharged());
            assertThat(detail.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo(report.detail().get(0).claimNumber());
        }
    }

    // ------------------------------------------------------------------
    // PDF is valid and contains the summary
    // ------------------------------------------------------------------

    @Test
    void pdfIsValidAndItsTextContainsThePeriodAndSummaryTotals() throws Exception {
        seedQ1_2025();
        ClaimReport report = reports.build(ReportPeriodType.QUARTER, 2025, 1);

        byte[] bytes = exporter.export(report, ExportFormat.PDF);
        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        String text = extractPdfText(bytes);
        assertThat(text).contains("Claims Report");
        assertThat(text).contains(report.period().label());          // "Q1 2025"
        assertThat(text).contains("Summary");
        // The summary totals rendered in the PDF match the report API.
        assertThat(text).contains(money(report.summary().totalCharged()));
        assertThat(text).contains(money(report.summary().totalPaid()));
        assertThat(text).contains(money(report.summary().outstandingReceivable()));
    }

    // ------------------------------------------------------------------
    // Filenames encode the period
    // ------------------------------------------------------------------

    @Test
    void filenamesEncodeTheQuarterAndTheYear() {
        seedQ1_2025();
        ClaimReport quarter = reports.build(ReportPeriodType.QUARTER, 2025, 1);
        assertThat(exporter.filename(quarter, ExportFormat.CSV)).isEqualTo("claims-report-Q1-2025.csv");
        assertThat(exporter.filename(quarter, ExportFormat.XLSX)).isEqualTo("claims-report-Q1-2025.xlsx");
        assertThat(exporter.filename(quarter, ExportFormat.PDF)).isEqualTo("claims-report-Q1-2025.pdf");

        ClaimReport annual = reports.build(ReportPeriodType.YEAR, 2025, null);
        assertThat(exporter.filename(annual, ExportFormat.CSV)).isEqualTo("claims-report-FY2025.csv");
        assertThat(exporter.filename(annual, ExportFormat.PDF)).isEqualTo("claims-report-FY2025.pdf");
    }

    // ------------------------------------------------------------------
    // Annual export rolls up the whole year and its totals match the API
    // ------------------------------------------------------------------

    @Test
    void annualExportTotalsMatchTheReportApiAcrossAllQuarters() throws Exception {
        // One claim in each quarter of 2025, plus one in 2024 that must not leak.
        inTx(() -> {
            persistClaim("CLM-2025-Q1", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 2), "100.00", "100.00", "100.00");
            persistClaim("CLM-2025-Q2", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 2), "200.00", "200.00", "200.00");
            persistClaim("CLM-2025-Q3", ClaimStatus.SUBMITTED, "21",
                    LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 2), "300.00", "0.00", "0.00");
            persistClaim("CLM-2025-Q4", ClaimStatus.PARTIALLY_PAID, "11",
                    LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 2), "400.00", "150.00", "150.00");
            persistClaim("CLM-2024-Q4", ClaimStatus.PAID, "11",
                    LocalDate.of(2024, 12, 20), LocalDate.of(2024, 12, 21), "999.00", "999.00", "999.00");
        });

        ClaimReport annual = reports.build(ReportPeriodType.YEAR, 2025, null);

        // CSV: parsed charge column of the detail block sums to the annual total,
        // and the 2024 claim is absent.
        List<String[]> rows = parseCsv(new String(exporter.export(annual, ExportFormat.CSV), StandardCharsets.UTF_8));
        List<String[]> detailRows = detailRowsOf(rows);
        assertThat(detailRows).extracting(r -> r[0])
                .containsExactlyInAnyOrder("CLM-2025-Q1", "CLM-2025-Q2", "CLM-2025-Q3", "CLM-2025-Q4")
                .doesNotContain("CLM-2024-Q4");
        BigDecimal csvChargeSum = detailRows.stream()
                .map(r -> new BigDecimal(r[8])).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(csvChargeSum).isEqualByComparingTo(annual.summary().totalCharged());
        assertThat(annual.summary().totalCharged()).isEqualByComparingTo("1000.00");

        // XLSX totals cell equals the API total.
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(exporter.export(annual, ExportFormat.XLSX)))) {
            Row header = findRow(wb.getSheet("Summary"), "Total Claims");
            Row totals = wb.getSheet("Summary").getRow(header.getRowNum() + 1);
            assertThat(BigDecimal.valueOf(totals.getCell(1).getNumericCellValue()))
                    .isEqualByComparingTo(annual.summary().totalCharged());
        }
    }

    // ------------------------------------------------------------------
    // The period boundary — a claim outside it appears in NO export
    // ------------------------------------------------------------------

    @Test
    void claimOutsideThePeriodAppearsInNoExportFormat() throws Exception {
        inTx(() -> {
            persistClaim("CLM-INSIDE", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 1, 15), LocalDate.of(2025, 1, 16), "100.00", "100.00", "100.00");
            // Q2 claim — outside Q1 — must not appear when Q1 is requested.
            persistClaim("CLM-OUTSIDE-Q2", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 2), "555.00", "555.00", "555.00");
            // Prior-year claim — outside 2025 — must not appear either.
            persistClaim("CLM-OUTSIDE-2024", ClaimStatus.PAID, "11",
                    LocalDate.of(2024, 12, 31), LocalDate.of(2024, 12, 31), "777.00", "777.00", "777.00");
        });

        ClaimReport q1 = reports.build(ReportPeriodType.QUARTER, 2025, 1);
        assertThat(q1.detail()).extracting(ClaimListItem::claimNumber).containsExactly("CLM-INSIDE");

        String csv = new String(exporter.export(q1, ExportFormat.CSV), StandardCharsets.UTF_8);
        String xlsxText = allXlsxText(exporter.export(q1, ExportFormat.XLSX));
        String pdfText = extractPdfText(exporter.export(q1, ExportFormat.PDF));

        for (String out : List.of(csv, xlsxText, pdfText)) {
            assertThat(out).contains("CLM-INSIDE");
            assertThat(out).doesNotContain("CLM-OUTSIDE-Q2");
            assertThat(out).doesNotContain("CLM-OUTSIDE-2024");
            // The out-of-period charge value never leaks into any export.
            assertThat(out).doesNotContain("555.00");
            assertThat(out).doesNotContain("777.00");
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void seedQ1_2025() {
        inTx(() -> {
            persistClaim("CLM-2025-000001", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 12), "100.00", "80.00", "80.00");
            persistClaim("CLM-2025-000002", ClaimStatus.SUBMITTED, "21",
                    LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 3), "250.50", "0.00", "0.00");
            persistClaim("CLM-2025-000003", ClaimStatus.PARTIALLY_PAID, "11",
                    LocalDate.of(2025, 3, 30), LocalDate.of(2025, 3, 31), "400.00", "120.00", "120.00");
        });
    }

    private static String money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Minimal RFC 4180 parser: splits records on unquoted CRLF, fields on unquoted commas. */
    private static List<String[]> parseCsv(String csv) {
        List<String[]> records = new ArrayList<>();
        List<String> field = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQuotes = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                field.add(cur.toString()); cur.setLength(0);
            } else if (c == '\r') {
                // consume the paired \n
                if (i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
                field.add(cur.toString()); cur.setLength(0);
                records.add(field.toArray(new String[0])); field = new ArrayList<>();
            } else if (c == '\n') {
                field.add(cur.toString()); cur.setLength(0);
                records.add(field.toArray(new String[0])); field = new ArrayList<>();
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0 || !field.isEmpty()) {
            field.add(cur.toString());
            records.add(field.toArray(new String[0]));
        }
        return records;
    }

    /** First column value of the summary row whose label is {@code key}. */
    private static String cellValue(List<String[]> rows, String key) {
        for (String[] r : rows) {
            if (r.length >= 2 && r[0].equals(key)) return r[1];
        }
        throw new AssertionError("No summary row labelled '" + key + "'");
    }

    /** Detail rows: everything after the "Claim Number,Status,..." header row. */
    private static List<String[]> detailRowsOf(List<String[]> rows) {
        int headerIdx = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).length > 0 && rows.get(i)[0].equals("Claim Number")) { headerIdx = i; break; }
        }
        if (headerIdx < 0) throw new AssertionError("No detail header row found");
        List<String[]> out = new ArrayList<>();
        for (int i = headerIdx + 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            if (r.length >= 9 && !r[0].isBlank()) out.add(r);
        }
        return out;
    }

    private static Row findRow(Sheet sheet, String firstCellText) {
        for (Row row : sheet) {
            if (row.getCell(0) != null
                    && row.getCell(0).getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                    && firstCellText.equals(row.getCell(0).getStringCellValue())) {
                return row;
            }
        }
        return null;
    }

    private static String allXlsxText(byte[] bytes) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            for (Sheet sheet : wb) {
                for (Row row : sheet) {
                    for (org.apache.poi.ss.usermodel.Cell cell : row) {
                        switch (cell.getCellType()) {
                            case STRING -> sb.append(cell.getStringCellValue());
                            case NUMERIC -> sb.append(BigDecimal.valueOf(cell.getNumericCellValue())
                                    .setScale(2, RoundingMode.HALF_UP).toPlainString());
                            default -> { }
                        }
                        sb.append(' ');
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static String extractPdfText(byte[] bytes) throws Exception {
        PdfReader reader = new PdfReader(bytes);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                sb.append(extractor.getTextFromPage(page)).append('\n');
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    private void inTx(Runnable body) {
        em.getTransaction().begin();
        try {
            body.run();
            em.flush();
            em.getTransaction().commit();
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.clear();
        }
    }

    private Patient persistPatient() {
        Patient p = new Patient();
        p.setMrn("MRN-1");
        p.setFirstName("Ada");
        p.setLastName("Lovelace");
        p.setDateOfBirth(LocalDate.of(1990, 1, 1));
        em.persist(p);
        return p;
    }

    private Provider persistProvider() {
        Provider pr = new Provider();
        pr.setNpi("1234567890");
        pr.setFirstName("Grace");
        pr.setLastName("Hopper");
        pr.setActive(true);
        em.persist(pr);
        return pr;
    }

    private Payer persistPayer() {
        Payer pay = new Payer();
        pay.setPayerCode("PAY1");
        pay.setName("Acme Health");
        pay.setPlanType(PlanType.COMMERCIAL);
        pay.setActive(true);
        em.persist(pay);
        return pay;
    }

    private void persistClaim(String number, ClaimStatus status, String pos,
                              LocalDate from, LocalDate to,
                              String charge, String allowed, String paid) {
        Claim c = new Claim();
        c.setClaimNumber(number);
        c.setPatient(patient);
        c.setProvider(provider);
        c.setPayer(payer);
        c.setStatus(status);
        c.setPlaceOfService(pos);
        c.setServiceDateFrom(from);
        c.setServiceDateTo(to);
        c.setTotalCharge(new BigDecimal(charge));
        c.setAllowedAmount(new BigDecimal(allowed));
        c.setPaidAmount(new BigDecimal(paid));
        c.setCreatedBy("test");
        em.persist(c);
    }
}
