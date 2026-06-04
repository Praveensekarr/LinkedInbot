package com.example.linkedInbot.controller;

import com.example.linkedInbot.model.AppliedJob;
import com.example.linkedInbot.repository.AppliedJobRepository;
import com.example.linkedInbot.service.Excel.ExcelExportService;
import com.example.linkedInbot.service.Orchestrator.JobOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.List;


@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bot")
public class LoginController {

    private final ExcelExportService excelService;
    private final JobOrchestratorService orchestratorService;
    private final AppliedJobRepository repo;

    @GetMapping("/run")
    public String runBot() throws InterruptedException {
        orchestratorService.runFullFlow();
        return "Bot Executed";
    }

    @GetMapping("/export")
    public String export() {
        List<AppliedJob> allJobs = repo.findAll();
        try {
            String projectPath = System.getProperty("user.dir");
            String filePath = projectPath + File.separator + "LinkedIn_Jobs_Report.xlsx";

            excelService.exportJobsToExcel(allJobs, filePath);
            return "Excel generated successfully at: " + filePath;
        } catch (IOException e) {
            log.error("Export failed: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
