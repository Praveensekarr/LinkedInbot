//package com.example.linkedInbot.service.Orchestrator;
//
//import com.example.linkedInbot.model.AppliedJob;
//import com.example.linkedInbot.service.Excel.ExcelExportService;
//import com.example.linkedInbot.service.fetch.CompanyJobScraperService;
//import com.example.linkedInbot.service.login.LinkedInLoginService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.openqa.selenium.WebDriver;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class JobOrchestratorService {
//
//    private final LinkedInLoginService loginService;
//    private final CompanyJobScraperService scraperService;
//    private final ExcelExportService excelService;
//
//    @Value("${linkedin.email}")
//    private String email;
//
//    @Value("${linkedin.password}")
//    private String password;
//
//    public void runFullFlow() {
//        WebDriver driver = null;
//
//        try {
//            log.info("Starting LinkedIn Bot Flow (Page-by-Page Strategy)...");
//            driver = loginService.login(email, password);
//
//            if (driver == null || !isSessionAlive(driver)) {
//                log.error("Login session could not be established. Aborting.");
//                return;
//            }
//
//            Thread.sleep(5000);
//
//            List<AppliedJob> sessionJobs = scraperService.scrapeAndProcessPages(driver, 1);
//
//            if (sessionJobs != null && !sessionJobs.isEmpty()) {
//                generateSessionExcel(sessionJobs);
//            } else {
//                log.warn("No jobs were processed during this session. Skipping Excel export.");
//            }
//
//            log.info("Automation flow completed successfully.");
//
//        } catch (InterruptedException e) {
//            log.error("Process interrupted: {}", e.getMessage());
//            Thread.currentThread().interrupt();
//        } catch (Exception e) {
//            log.error("Unexpected error in Orchestrator: {}", e.getMessage(), e);
//        } finally {
//            if (driver != null) {
//                try {
//                    driver.quit();
//                    log.info("Browser session closed safely.");
//                } catch (Exception ignored) {}
//            }
//        }
//    }
//
//    private void generateSessionExcel(List<AppliedJob> jobs) {
//        try {
//            String projectPath = System.getProperty("user.dir");
//
//            String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
//            String filePath = projectPath + java.io.File.separator + "Session_Report_" + timestamp + ".xlsx";
//
//            excelService.exportJobsToExcel(jobs, filePath);
//            log.info("SUCCESS: Session Excel created with {} jobs at: {}", jobs.size(), filePath);
//        } catch (Exception e) {
//            log.error("Failed to generate Session Excel: {}", e.getMessage());
//        }
//    }
//
//    private boolean isSessionAlive(WebDriver driver) {
//        try {
//            driver.getTitle();
//            return true;
//        } catch (Exception e) {
//            return false;
//        }
//    }
//}

package com.example.linkedInbot.service.Orchestrator;

import com.example.linkedInbot.model.AppliedJob;
import com.example.linkedInbot.model.BotConfig;
import com.example.linkedInbot.service.BotConfig.BotConfigStore;
import com.example.linkedInbot.service.DatabaseStore.JobStoreService;
import com.example.linkedInbot.service.Excel.ExcelExportService;
import com.example.linkedInbot.service.fetch.CompanyJobScraperService;
import com.example.linkedInbot.service.login.LinkedInLoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobOrchestratorService {

    private final LinkedInLoginService loginService;
    private final CompanyJobScraperService scraperService;
    private final JobStoreService jobStoreService;   // replaces ExcelExportService
    private final BotConfigStore configStore;

    public void runFullFlow() {
        BotConfig cfg = configStore.get();
        if (cfg == null) {
            log.error("BotConfig is not set. Call POST /api/config first.");
            return;
        }

        WebDriver driver = null;
        try {
            log.info("Starting LinkedIn Bot Flow...");
            driver = loginService.login(cfg.getEmail(), cfg.getPassword());

            if (driver == null || !isSessionAlive(driver)) {
                log.error("Login session could not be established. Aborting.");
                return;
            }

            Thread.sleep(5000);

            scraperService.scrapeAndProcessPages(driver, 1);

            log.info("Automation flow completed successfully.");

        } catch (InterruptedException e) {
            log.error("Process interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Unexpected error in Orchestrator: {}", e.getMessage(), e);
        } finally {
            if (driver != null) {
                try { driver.quit(); log.info("Browser session closed safely."); }
                catch (Exception ignored) {}
            }

            log.info("Starting automatic export and DB clear...");
            String outputDir = System.getProperty("user.dir");
            String savedFile = jobStoreService.exportAndClear(outputDir);
            if (savedFile != null) {
                log.info("Auto-export done → {}. DB is now clear for next person.", savedFile);
            } else {
                log.warn("Auto-export skipped (empty DB or export failed). DB was NOT cleared.");
            }
        }
    }

    private boolean isSessionAlive(WebDriver driver) {
        try { driver.getTitle(); return true; }
        catch (Exception e) { return false; }
    }
}
