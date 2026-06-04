package com.example.linkedInbot.service.Excel;

import com.example.linkedInbot.model.AppliedJob;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;


@Service
public class ExcelExportService {

    public void exportJobsToExcel(List<AppliedJob> jobs, String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Scraped Jobs");

            Row headerRow = sheet.createRow(0);
            String[] columns = {"ID", "Job Name", "Company", "Status", "URL", "Date"};
            for (int i = 0; i < columns.length; i++) {
                headerRow.createCell(i).setCellValue(columns[i]);
            }

            int rowNum = 1;
            for (AppliedJob job : jobs) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(1).setCellValue(job.getJobname());
                row.createCell(2).setCellValue(job.getCompanyName());
                row.createCell(3).setCellValue(job.getStatus());
                row.createCell(4).setCellValue(job.getJobUrl());
                row.createCell(5).setCellValue(job.getAppliedTime().toString());
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
    }
}