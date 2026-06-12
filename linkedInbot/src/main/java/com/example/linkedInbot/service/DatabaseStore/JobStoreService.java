package com.example.linkedInbot.service.DatabaseStore;

import com.example.linkedInbot.model.AppliedJob;
import com.example.linkedInbot.model.BotConfig;
import com.example.linkedInbot.service.BotConfig.BotConfigStore;
import com.example.linkedInbot.service.Excel.ExcelExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobStoreService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ExcelExportService excelExportService;
    private final BotConfigStore configStore;

    /** jobs recorded so far in this run, not yet flushed to disk */
    private final List<AppliedJob> currentRun = new ArrayList<>();

    /** job URLs already recorded in today's file for the current profile name */
    private Set<String> todaysUrls = null;

    /** the (profileName, date) this todaysUrls set was loaded for - reload if either changes */
    private String loadedKey = null;

    /** next ID to assign for the currently-loaded (profileName, date) - reset on each reload */
    private long nextId = 1;

    private String profileName() {
        BotConfig cfg = configStore.get();
        String firstName = "";
        String lastName = "";
        if (cfg != null) {
            firstName = cfg.getFirstName() != null ? cfg.getFirstName().trim() : "";
            lastName  = cfg.getLastName()  != null ? cfg.getLastName().trim()  : "";
        }
        String namePart = (firstName + " " + lastName).trim();
        if (namePart.isEmpty()) namePart = "Profile";
        return namePart.replaceAll("\\s+", "_");
    }

    private String today() {
        return LocalDate.now().format(DATE_FMT);
    }

    private String currentFileName() {
        return profileName() + "_Profile_Applied_Jobs_" + today() + ".xlsx";
    }

    private Set<String> getOrLoadTodaysUrls() {
        String key = profileName() + "|" + today();

        if (!key.equals(loadedKey)) {
            String fileName = currentFileName();
            Set<String> urls = excelExportService.loadJobUrlsFromFile(fileName);
            todaysUrls = new HashSet<>(urls);
            loadedKey = key;

            long maxId = excelExportService.getMaxIdInFile(fileName);
            nextId = maxId + 1;

            log.info("[getOrLoadTodaysUrls] Loaded {} URL(s) from '{}', next ID = {}", urls.size(), fileName, nextId);
        }

        return todaysUrls;
    }

    public synchronized void saveRecord(String jobUrl, String jobTitle, String companyName, String status) {
        try {
            Set<String> urls = getOrLoadTodaysUrls();

            if (urls.contains(jobUrl)) {
                log.info("Job already recorded in today's file for {}, skipping: {}", profileName(), jobUrl);
                return;
            }

            AppliedJob record = new AppliedJob();
            record.setId(nextId++);
            record.setJobUrl(jobUrl);
            record.setJobname(jobTitle != null ? jobTitle : "Unknown Title");
            record.setCompanyName(companyName != null ? companyName : "Unknown Company");
            record.setAppliedTime(LocalDateTime.now());
            record.setStatus(status);

            currentRun.add(record);
            urls.add(jobUrl);

            log.info("Record Saved [{}] -> Title: {}, Company: {}, Status: {}", profileName(), jobTitle, companyName, status);

        } catch (Exception e) {
            log.error("Failed to save record for {} : {}", jobUrl, e.getMessage());
        }
    }

    public synchronized boolean isAlreadyApplied(String jobUrl) {
        return getOrLoadTodaysUrls().contains(jobUrl);
    }

    public synchronized String exportAndClear(String outputDir) {
        if (currentRun.isEmpty()) {
            log.warn("[exportAndClear] No new records this run - nothing to export.");
            return null;
        }

        String fileName = currentFileName();

        File savedFile;
        try {
            savedFile = excelExportService.appendJobsToExcel(currentRun, fileName);
            log.info("[exportAndClear] Excel updated -> {} (+{} new record(s))", savedFile.getAbsolutePath(), currentRun.size());
        } catch (Exception e) {
            log.error("[exportAndClear] Excel export failed: {}", e.getMessage());
            return null;
        }

        currentRun.clear();
        log.info("[exportAndClear] In-memory run records cleared. Today's dedup set retained ({} URLs).",
                todaysUrls == null ? 0 : todaysUrls.size());

        return savedFile.getAbsolutePath();
    }
}
