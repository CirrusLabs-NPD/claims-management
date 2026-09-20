package com.claire.claims.service.export;

/**
 * The download formats a claims report can be exported as. Each carries the
 * MIME type to set on the response and the file extension for the download
 * filename, so the controller never hardcodes either.
 */
public enum ExportFormat {

    CSV("text/csv", "csv"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    PDF("application/pdf", "pdf");

    private final String contentType;
    private final String extension;

    ExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
