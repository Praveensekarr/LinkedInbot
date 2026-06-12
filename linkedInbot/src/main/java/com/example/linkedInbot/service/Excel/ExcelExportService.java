package com.example.linkedInbot.service.Excel;

import com.example.linkedInbot.config.AppDataLocation;
import com.example.linkedInbot.model.AppliedJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class ExcelExportService {

    private static final String[] COLUMNS = {"ID", "Job Name", "Company", "Status", "URL", "Date"};
    private static final int URL_COL = 4;
    private static final String SHEET_NAME = "Applied Jobs";

    public File appendJobsToExcel(List<AppliedJob> jobs, String fileName) throws IOException {
        File outputFile = AppDataLocation.resolve(fileName);

        List<AppliedJob> existing = readExistingRows(outputFile);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.length; i++) {
                headerRow.createCell(i).setCellValue(COLUMNS[i]);
            }

            int rowNum = 1;

            // Re-write existing rows first, preserving their IDs
            for (AppliedJob job : existing) {
                writeRow(sheet, rowNum++, job);
            }

            // Append new rows (IDs already assigned by JobStoreService)
            for (AppliedJob job : jobs) {
                writeRow(sheet, rowNum++, job);
            }

            for (int i = 0; i < COLUMNS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
                workbook.write(fileOut);
            }
        }

        return outputFile;
    }

    private void writeRow(Sheet sheet, int rowNum, AppliedJob job) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(job.getId());
        row.createCell(1).setCellValue(job.getJobname());
        row.createCell(2).setCellValue(job.getCompanyName());
        row.createCell(3).setCellValue(job.getStatus());
        row.createCell(4).setCellValue(job.getJobUrl());
        row.createCell(5).setCellValue(job.getAppliedTime().toString());
    }

    private List<AppliedJob> readExistingRows(File file) {
        List<AppliedJob> rows = new ArrayList<>();
        if (!file.exists()) return rows;

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return rows;

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                AppliedJob job = new AppliedJob();
                job.setId((long) getCellNumeric(row, 0));
                job.setJobname(getCellString(row, 1));
                job.setCompanyName(getCellString(row, 2));
                job.setStatus(getCellString(row, 3));
                job.setJobUrl(getCellString(row, 4));
                try {
                    job.setAppliedTime(java.time.LocalDateTime.parse(getCellString(row, 5)));
                } catch (Exception e) {
                    job.setAppliedTime(java.time.LocalDateTime.now());
                }
                rows.add(job);
            }
        } catch (Exception e) {
            log.warn("[readExistingRows] Could not read existing file {}: {}", file.getName(), e.getMessage());
        }
        return rows;
    }

    public Set<String> loadJobUrlsFromFile(String fileName) {
        Set<String> urls = new HashSet<>();
        File file = AppDataLocation.resolve(fileName);

        if (!file.exists()) {
            log.info("[loadJobUrlsFromFile] '{}' does not exist yet - starting fresh.", fileName);
            return urls;
        }

        for (AppliedJob job : readExistingRows(file)) {
            if (job.getJobUrl() != null && !job.getJobUrl().isBlank()) {
                urls.add(job.getJobUrl().trim());
            }
        }

        log.info("[loadJobUrlsFromFile] Loaded {} job URL(s) from '{}'.", urls.size(), fileName);
        return urls;
    }

    public long getMaxIdInFile(String fileName) {
        File file = AppDataLocation.resolve(fileName);
        if (!file.exists()) return 0;

        long maxId = 0;
        for (AppliedJob job : readExistingRows(file)) {
            if (job.getId() > maxId) maxId = job.getId();
        }
        return maxId;
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private double getCellNumeric(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return 0;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> cell.getNumericCellValue();
                case STRING -> Double.parseDouble(cell.getStringCellValue().trim());
                default -> 0;
            };
        } catch (Exception e) {
            return 0;
        }
    }
}