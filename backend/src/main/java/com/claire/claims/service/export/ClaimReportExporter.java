package com.claire.claims.service.export;

import com.claire.claims.dto.ClaimDtos.ClaimListItem;
import com.claire.claims.dto.ClaimDtos.StatusBucket;
import com.claire.claims.dto.ReportDtos.ClaimReport;
import com.claire.claims.dto.ReportDtos.ReportSummary;
import com.claire.claims.dto.ReportDtos.TypeBucket;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Renders a {@link ClaimReport} to a downloadable document in one of three
 * formats. Every format presents the SAME content the JSON report carries — the
 * rolled-up summary on top, then the underlying claim detail rows — so an export
 * can never disagree with the on-screen report it was taken from.
 *
 * The report handed in is already tenancy/period-scoped by
 * {@code ClaimReportService.build(...)}; this class only serialises it and never
 * queries the database, so there is no scoping decision to get wrong here.
 */
@Component
public class ClaimReportExporter {

    // Column order for the detail table, shared by every format so CSV, Excel and
    // PDF line up field-for-field.
    private static final String[] DETAIL_HEADERS = {
            "Claim Number", "Status", "Patient", "MRN", "Payer", "Provider",
            "Service From", "Service To", "Total Charge", "Paid", "Outstanding",
            "Submitted At", "Created At"
    };

    // ----- format dispatch ------------------------------------------------

    /** Serialise {@code report} to bytes in the requested {@code format}. */
    public byte[] export(ClaimReport report, ExportFormat format) {
        return switch (format) {
            case CSV -> toCsv(report);
            case XLSX -> toXlsx(report);
            case PDF -> toPdf(report);
        };
    }

    /**
     * Download filename for the report, e.g. {@code claims-report-Q1-2025.csv}
     * or {@code claims-report-FY2024.pdf}. The period label is normalised to a
     * filename-safe token (spaces to dashes).
     */
    public String filename(ClaimReport report, ExportFormat format) {
        String label = report.period().label().trim().replaceAll("\\s+", "-");
        return "claims-report-" + label + "." + format.extension();
    }

    // ----- CSV ------------------------------------------------------------

    private byte[] toCsv(ClaimReport report) {
        StringBuilder sb = new StringBuilder();
        ReportSummary s = report.summary();

        // Summary block.
        sb.append(csvRow("Claims Report", report.period().label()));
        sb.append(csvRow("Period", report.period().from() + " to " + report.period().to()));
        sb.append("\r\n");
        sb.append(csvRow("Summary"));
        sb.append(csvRow("Total Claims", String.valueOf(s.totalClaims())));
        sb.append(csvRow("Total Charged", money(s.totalCharged())));
        sb.append(csvRow("Total Paid", money(s.totalPaid())));
        sb.append(csvRow("Outstanding Receivable", money(s.outstandingReceivable())));
        sb.append("\r\n");

        sb.append(csvRow("By Status", "Description", "Count", "Total Charge", "Paid"));
        for (StatusBucket b : s.byStatus()) {
            sb.append(csvRow(b.status().name(), b.description(), String.valueOf(b.count()),
                    money(b.totalCharge()), money(b.paidAmount())));
        }
        sb.append("\r\n");

        sb.append(csvRow("By Type (Place of Service)", "Count", "Total Charge", "Paid"));
        for (TypeBucket b : s.byType()) {
            sb.append(csvRow(b.code(), String.valueOf(b.count()),
                    money(b.totalCharge()), money(b.paidAmount())));
        }
        sb.append("\r\n");

        // Detail block.
        sb.append(csvRow("Detail"));
        sb.append(csvRow(DETAIL_HEADERS));
        for (ClaimListItem c : report.detail()) {
            sb.append(csvRow(
                    nullSafe(c.claimNumber()),
                    c.status() == null ? "" : c.status().name(),
                    nullSafe(c.patientName()),
                    nullSafe(c.patientMrn()),
                    nullSafe(c.payerName()),
                    nullSafe(c.providerName()),
                    date(c.serviceDateFrom()),
                    date(c.serviceDateTo()),
                    money(c.totalCharge()),
                    money(c.paidAmount()),
                    money(c.outstanding()),
                    dateTime(c.submittedAt()),
                    dateTime(c.createdAt())));
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvRow(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvEscape(cells[i]));
        }
        return sb.append("\r\n").toString();
    }

    /** RFC 4180 quoting: wrap in quotes when the value has a comma, quote or newline. */
    private static String csvEscape(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    // ----- Excel ----------------------------------------------------------

    private byte[] toXlsx(ClaimReport report) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font boldFont = wb.createFont();
            boldFont.setBold(true);
            bold.setFont(boldFont);

            writeSummarySheet(wb, bold, report);
            writeDetailSheet(wb, bold, report);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Excel report", e);
        }
    }

    private void writeSummarySheet(Workbook wb, CellStyle bold, ClaimReport report) {
        Sheet sheet = wb.createSheet("Summary");
        ReportSummary s = report.summary();
        int r = 0;

        r = keyValueRow(sheet, r, bold, "Claims Report", report.period().label());
        r = keyValueRow(sheet, r, bold, "Period",
                report.period().from() + " to " + report.period().to());
        r++;

        headerRow(sheet, r++, bold, "Total Claims", "Total Charged", "Total Paid", "Outstanding");
        Row totals = sheet.createRow(r++);
        cell(totals, 0, s.totalClaims());
        cell(totals, 1, s.totalCharged());
        cell(totals, 2, s.totalPaid());
        cell(totals, 3, s.outstandingReceivable());
        r++;

        headerRow(sheet, r++, bold, "By Status", "Description", "Count", "Total Charge", "Paid");
        for (StatusBucket b : s.byStatus()) {
            Row row = sheet.createRow(r++);
            cell(row, 0, b.status().name());
            cell(row, 1, b.description());
            cell(row, 2, b.count());
            cell(row, 3, b.totalCharge());
            cell(row, 4, b.paidAmount());
        }
        r++;

        headerRow(sheet, r++, bold, "By Type (Place of Service)", "Count", "Total Charge", "Paid");
        for (TypeBucket b : s.byType()) {
            Row row = sheet.createRow(r++);
            cell(row, 0, b.code());
            cell(row, 1, b.count());
            cell(row, 2, b.totalCharge());
            cell(row, 3, b.paidAmount());
        }

        autoSize(sheet, 5);
    }

    private void writeDetailSheet(Workbook wb, CellStyle bold, ClaimReport report) {
        Sheet sheet = wb.createSheet("Detail");
        headerRow(sheet, 0, bold, DETAIL_HEADERS);

        int r = 1;
        for (ClaimListItem c : report.detail()) {
            Row row = sheet.createRow(r++);
            cell(row, 0, c.claimNumber());
            cell(row, 1, c.status() == null ? "" : c.status().name());
            cell(row, 2, c.patientName());
            cell(row, 3, c.patientMrn());
            cell(row, 4, c.payerName());
            cell(row, 5, c.providerName());
            cell(row, 6, date(c.serviceDateFrom()));
            cell(row, 7, date(c.serviceDateTo()));
            cell(row, 8, c.totalCharge());
            cell(row, 9, c.paidAmount());
            cell(row, 10, c.outstanding());
            cell(row, 11, dateTime(c.submittedAt()));
            cell(row, 12, dateTime(c.createdAt()));
        }

        autoSize(sheet, DETAIL_HEADERS.length);
    }

    private static int keyValueRow(Sheet sheet, int r, CellStyle bold, String key, String value) {
        Row row = sheet.createRow(r);
        Cell k = row.createCell(0);
        k.setCellValue(key);
        k.setCellStyle(bold);
        row.createCell(1).setCellValue(value);
        return r + 1;
    }

    private static void headerRow(Sheet sheet, int r, CellStyle bold, String... headers) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < headers.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(bold);
        }
    }

    private static void cell(Row row, int col, String value) {
        row.createCell(col).setCellValue(value == null ? "" : value);
    }

    private static void cell(Row row, int col, long value) {
        row.createCell(col).setCellValue(value);
    }

    private static void cell(Row row, int col, BigDecimal value) {
        row.createCell(col).setCellValue(value == null ? 0d : value.doubleValue());
    }

    private static void autoSize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // ----- PDF ------------------------------------------------------------

    private byte[] toPdf(ClaimReport report) {
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, out);
        doc.open();
        try {
            ReportSummary s = report.summary();
            Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);

            Paragraph title = new Paragraph("Claims Report — " + report.period().label(), h1);
            title.setSpacingAfter(4);
            doc.add(title);
            doc.add(new Paragraph(
                    "Period: " + report.period().from() + " to " + report.period().to(), normal));

            doc.add(spacer());
            doc.add(new Paragraph("Summary", h2));
            PdfPTable totals = new PdfPTable(4);
            totals.setWidthPercentage(100);
            totals.setSpacingBefore(4);
            headerCells(totals, "Total Claims", "Total Charged", "Total Paid", "Outstanding");
            bodyCell(totals, String.valueOf(s.totalClaims()));
            bodyCell(totals, money(s.totalCharged()));
            bodyCell(totals, money(s.totalPaid()));
            bodyCell(totals, money(s.outstandingReceivable()));
            doc.add(totals);

            doc.add(spacer());
            doc.add(new Paragraph("By Status", h2));
            PdfPTable byStatus = new PdfPTable(5);
            byStatus.setWidthPercentage(100);
            byStatus.setSpacingBefore(4);
            headerCells(byStatus, "Status", "Description", "Count", "Total Charge", "Paid");
            for (StatusBucket b : s.byStatus()) {
                bodyCell(byStatus, b.status().name());
                bodyCell(byStatus, b.description());
                bodyCell(byStatus, String.valueOf(b.count()));
                bodyCell(byStatus, money(b.totalCharge()));
                bodyCell(byStatus, money(b.paidAmount()));
            }
            doc.add(byStatus);

            doc.add(spacer());
            doc.add(new Paragraph("By Type (Place of Service)", h2));
            PdfPTable byType = new PdfPTable(4);
            byType.setWidthPercentage(100);
            byType.setSpacingBefore(4);
            headerCells(byType, "Code", "Count", "Total Charge", "Paid");
            for (TypeBucket b : s.byType()) {
                bodyCell(byType, b.code());
                bodyCell(byType, String.valueOf(b.count()));
                bodyCell(byType, money(b.totalCharge()));
                bodyCell(byType, money(b.paidAmount()));
            }
            doc.add(byType);

            doc.add(spacer());
            doc.add(new Paragraph("Detail (" + report.detail().size() + " claims)", h2));
            PdfPTable detail = new PdfPTable(DETAIL_HEADERS.length);
            detail.setWidthPercentage(100);
            detail.setSpacingBefore(4);
            headerCells(detail, DETAIL_HEADERS);
            for (ClaimListItem c : report.detail()) {
                bodyCell(detail, nullSafe(c.claimNumber()));
                bodyCell(detail, c.status() == null ? "" : c.status().name());
                bodyCell(detail, nullSafe(c.patientName()));
                bodyCell(detail, nullSafe(c.patientMrn()));
                bodyCell(detail, nullSafe(c.payerName()));
                bodyCell(detail, nullSafe(c.providerName()));
                bodyCell(detail, date(c.serviceDateFrom()));
                bodyCell(detail, date(c.serviceDateTo()));
                bodyCell(detail, money(c.totalCharge()));
                bodyCell(detail, money(c.paidAmount()));
                bodyCell(detail, money(c.outstanding()));
                bodyCell(detail, dateTime(c.submittedAt()));
                bodyCell(detail, dateTime(c.createdAt()));
            }
            doc.add(detail);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private static Paragraph spacer() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingBefore(6);
        return p;
    }

    private static void headerCells(PdfPTable table, String... headers) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, f));
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(cell);
        }
    }

    private static void bodyCell(PdfPTable table, String value) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 8);
        table.addCell(new PdfPCell(new Phrase(value == null ? "" : value, f)));
    }

    // ----- shared formatting ---------------------------------------------

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }

    private static String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private static String dateTime(OffsetDateTime value) {
        return value == null ? "" : value.toString();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
