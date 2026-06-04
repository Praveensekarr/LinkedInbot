package com.example.linkedInbot.controller;

import com.example.linkedInbot.model.BotConfig;
import com.example.linkedInbot.service.BotConfig.BotConfigStore;
import com.example.linkedInbot.service.Orchestrator.JobOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")   // Allow the frontend (React dev-server or same origin) to call this
public class BotConfigController {

    private final BotConfigStore configStore;
    private final JobOrchestratorService orchestratorService;

    // ── Save / update config ──────────────────────────────────────────────────
    @PostMapping("/api/config")
    public ResponseEntity<Map<String, String>> saveConfig(@RequestBody BotConfig config) {
        if (config.getEmail() == null || config.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }
        if (config.getPassword() == null || config.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
        }
        if (config.getSearchUrl() == null || config.getSearchUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "LinkedIn search URL is required"));
        }

        configStore.save(config);
        log.info("BotConfig saved for user: {}", config.getEmail());
        return ResponseEntity.ok(Map.of("message", "Configuration saved successfully"));
    }

    // ── Read current config (safe – password masked) ──────────────────────────
    @GetMapping("/api/config")
    public ResponseEntity<?> getConfig() {
        BotConfig cfg = configStore.get();
        if (cfg == null) {
            return ResponseEntity.ok(Map.of("configured", false));
        }
        // Return a safe copy with password masked
        BotConfig safe = new BotConfig();
        safe.setEmail(cfg.getEmail());
        safe.setPassword("••••••••");
        safe.setSearchUrl(cfg.getSearchUrl());
        safe.setTargetLocation(cfg.getTargetLocation());
        safe.setCurrentCity(cfg.getCurrentCity());
        safe.setCurrentCtc(cfg.getCurrentCtc());
        safe.setCurrentCtcMonthly(cfg.getCurrentCtcMonthly());
        safe.setExpectedCtc(cfg.getExpectedCtc());
        safe.setExpectedCtcMonthly(cfg.getExpectedCtcMonthly());
        safe.setExperienceYears(cfg.getExperienceYears());
        safe.setExperienceMonths(cfg.getExperienceMonths());
        safe.setNoticePeriod(cfg.getNoticePeriod());
        safe.setWorkTitle(cfg.getWorkTitle());
        safe.setDefaultCompany(cfg.getDefaultCompany());
        safe.setPortfolioUrl(cfg.getPortfolioUrl());
        safe.setDefaultNumber(cfg.getDefaultNumber());
        safe.setFirstName(cfg.getFirstName());
        safe.setLastName(cfg.getLastName());
        safe.setPhoneNumber(cfg.getPhoneNumber());
        safe.setPhotoPath(cfg.getPhotoPath());
        safe.setCustomFields(cfg.getCustomFields());
        return ResponseEntity.ok(safe);
    }

    // ── Start the bot ─────────────────────────────────────────────────────────
    @PostMapping("/api/bot/start")
    public ResponseEntity<Map<String, String>> startBot() {
        if (!configStore.isConfigured()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Bot is not configured yet. Please fill the configuration form first."));
        }
        // Run in a background thread so the HTTP response returns immediately
        new Thread(() -> {
            try {
                orchestratorService.runFullFlow();
            } catch (Exception e) {
                log.error("Bot run failed: {}", e.getMessage(), e);
            }
        }, "bot-runner").start();

        return ResponseEntity.ok(Map.of("message", "Bot started successfully. Check server logs for progress."));
    }
}
