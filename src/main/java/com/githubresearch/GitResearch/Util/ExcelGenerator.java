package com.githubresearch.GitResearch.Util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ExcelGenerator {
    private static final int MAX_CELL_LENGTH = 2000;
    private static final String OUTPUT_DIR = "reports";
    private static final String FILE_PREFIX = "GitHubCommits_";

    public static File generateExcel(List<CommitData> commitDataList) throws IOException {
        if (commitDataList == null || commitDataList.isEmpty()) {
            throw new IllegalArgumentException("Commit data list cannot be null or empty");
        }

        // Create output directory if it doesn't exist
        File outputDir = new File(OUTPUT_DIR);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outputDir.getAbsolutePath());
        }

        // Create timestamped filename
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = FILE_PREFIX + timestamp + ".xlsx";
        File outputFile = new File(outputDir, fileName);

        System.out.println("Generating Excel report to: " + outputFile.getAbsolutePath());

        try (Workbook workbook = new XSSFWorkbook()) {
            createExcelSheet(workbook, commitDataList);
            
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                workbook.write(out);
                System.out.println("Successfully generated: " + outputFile.getName());
                return outputFile;
            }
        } catch (Exception e) {
            System.err.println("Error generating Excel file: " + e.getMessage());
            throw new IOException("Failed to generate Excel file", e);
        }
    }

    private static void createExcelSheet(Workbook workbook, List<CommitData> data) {
        Sheet sheet = workbook.createSheet("GitHub Commits");
        
        // Create header
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Commit URL", "Commit Hash", "Author", "Date", "Message"};
        CellStyle headerStyle = createHeaderStyle(workbook);
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Add data rows
        CellStyle dateStyle = createDateStyle(workbook);
        for (int i = 0; i < data.size(); i++) {
            CommitData commit = data.get(i);
            Row row = sheet.createRow(i + 1);
            
            row.createCell(0).setCellValue(commit.getUrl());
            row.createCell(1).setCellValue(commit.getHash());
            row.createCell(2).setCellValue(commit.getAuthor());
            
            Cell dateCell = row.createCell(3);
            dateCell.setCellValue(commit.getDate());
            dateCell.setCellStyle(dateStyle);
            
            row.createCell(4).setCellValue(limitTextLength(commit.getMessage()));
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 3000) {
                sheet.setColumnWidth(i, 3000); // Minimum width
            }
        }
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper()
            .createDataFormat()
            .getFormat("yyyy-mm-dd hh:mm:ss"));
        return style;
    }

    private static String limitTextLength(String text) {
        return (text != null && text.length() > MAX_CELL_LENGTH) 
            ? text.substring(0, MAX_CELL_LENGTH - 3) + "..."
            : text;
    }

    public static class CommitData {
        private final String url, hash, author, date, message;
        
        public CommitData(String url, String hash, String author, String date, String message) {
            this.url = url;
            this.hash = hash;
            this.author = author;
            this.date = date;
            this.message = message;
        }
        
        // Getters
        public String getUrl() { return url; }
        public String getHash() { return hash; }
        public String getAuthor() { return author; }
        public String getDate() { return date; }
        public String getMessage() { return message; }
    }
}