package com.example.linkedInbot.service.DatabaseStore;

import com.example.linkedInbot.model.AppliedJob;
import com.example.linkedInbot.repository.AppliedJobRepository;
import com.example.linkedInbot.service.Excel.ExcelExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobStoreService {

    private final AppliedJobRepository repo;
    private final ExcelExportService excelExportService;

    public void saveRecord(String jobUrl, String jobTitle, String companyName, String status) {
        try {
            if (isAlreadyApplied(jobUrl)) {
                log.info("Job already exists in database, skipping save: {}", jobUrl);
                return;
            }

            AppliedJob record = new AppliedJob();
            record.setJobUrl(jobUrl);
            record.setJobname(jobTitle != null ? jobTitle : "Unknown Title");
            record.setCompanyName(companyName != null ? companyName : "Unknown Company");
            record.setAppliedTime(LocalDateTime.now());
            record.setStatus(status);

            repo.save(record);
            log.info("Record Saved -> Title: {}, Company: {}, Status: {}", jobTitle, companyName, status);

        } catch (Exception e) {
            log.error("Failed to save record for {} : {}", jobUrl, e.getMessage());
        }
    }

    public boolean isAlreadyApplied(String jobUrl) {
        return repo.existsByJobUrl(jobUrl);
    }

    public String exportAndClear(String outputDir) {
        List<AppliedJob> allJobs = repo.findAll();

        if (allJobs.isEmpty()) {
            log.warn("[exportAndClear] No records in DB — nothing to export or clear.");
            return null;
        }

        // Build timestamped filename so each person's run is a separate file
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
        String filePath = outputDir + java.io.File.separator + "Applied_Jobs_" + timestamp + ".xlsx";

        try {
            excelExportService.exportJobsToExcel(allJobs, filePath);
            log.info("[exportAndClear] Excel saved → {} ({} records)", filePath, allJobs.size());
        } catch (Exception e) {
            log.error("[exportAndClear] Excel export failed: {}", e.getMessage());
            // Do NOT clear the DB if export failed — data would be lost with no file
            return null;
        }

        // Only truncate after a confirmed successful export
        try {
            repo.truncateTable();
            log.info("[exportAndClear] DB cleared — table is now empty for the next person.");
        } catch (Exception e) {
            log.error("[exportAndClear] Truncate failed (Excel is safe): {}", e.getMessage());
        }

        return filePath;
    }
}

