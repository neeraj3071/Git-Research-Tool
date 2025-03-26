package com.githubresearch.GitResearch.Util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class ExcelGenerator {
    private static final String FILE_NAME = "GitHubCommits.xlsx";
    private static final int MAX_CELL_LENGTH = 2000;

    public static void generateExcel(List<CommitData> commitDataList) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("GitHub Commits");

            // Create header row
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Commit URL");
            headerRow.createCell(1).setCellValue("Commit Hash");
            headerRow.createCell(2).setCellValue("Author");
            headerRow.createCell(3).setCellValue("Date");
            headerRow.createCell(4).setCellValue("Message");

            // Create data rows
            for (int i = 0; i < commitDataList.size(); i++) {
                CommitData commitData = commitDataList.get(i);
                Row row = sheet.createRow(i + 1);
                
                row.createCell(0).setCellValue(commitData.getUrl());
                row.createCell(1).setCellValue(commitData.getHash());
                row.createCell(2).setCellValue(commitData.getAuthor());
                row.createCell(3).setCellValue(commitData.getDate());
                
                // Truncate commit message if it's too long
                Cell messageCell = row.createCell(4);
                messageCell.setCellValue(limitTextLength(commitData.getMessage()));
            }

            // Auto-size columns
            for (int i = 0; i < 5; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to file
            try (FileOutputStream outputStream = new FileOutputStream(FILE_NAME)) {
                workbook.write(outputStream);
            }
        }
    }

    private static String limitTextLength(String text) {
        if (text != null && text.length() > MAX_CELL_LENGTH) {
            return text.substring(0, MAX_CELL_LENGTH - 3) + "...";
        }
        return text;
    }

    public static class CommitData {
        private String url;
        private String hash;
        private String author;
        private String date;
        private String message;

        public CommitData(String url, String hash, String author, String date, String message) {
            this.url = url;
            this.hash = hash;
            this.author = author;
            this.date = date;
            this.message = message;
        }

        public String getUrl() {
            return url;
        }

        public String getHash() {
            return hash;
        }

        public String getAuthor() {
            return author;
        }

        public String getDate() {
            return date;
        }

        public String getMessage() {
            return message;
        }
    }
}