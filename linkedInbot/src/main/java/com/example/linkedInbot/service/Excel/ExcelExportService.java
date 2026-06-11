package com.example.linkedInbot.service.Excel;

import com.example.linkedInbot.config.AppDataLocation;
import com.example.linkedInbot.model.AppliedJob;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;


@Service
public class ExcelExportService {

    public File exportJobsToExcel(List<AppliedJob> jobs, String filePath) throws IOException {
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
                row.createCell(0).setCellValue(job.getId());
                row.createCell(1).setCellValue(job.getJobname());
                row.createCell(2).setCellValue(job.getCompanyName());
                row.createCell(3).setCellValue(job.getStatus());
                row.createCell(4).setCellValue(job.getJobUrl());
                row.createCell(5).setCellValue(job.getAppliedTime().toString());
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // ── Redirect output to ~/LinkedInBotData/ instead of the project
            //    folder. Only the filename portion of `filePath` is used —
            //    any directory the caller passed in is ignored, so the file
            //    always lands in the external data folder alongside profiles.xlsx.
            String fileName = new File(filePath).getName();
            File outputFile = AppDataLocation.resolve(fileName);

            try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
                workbook.write(fileOut);
            }
            return outputFile;
        }
    }
}