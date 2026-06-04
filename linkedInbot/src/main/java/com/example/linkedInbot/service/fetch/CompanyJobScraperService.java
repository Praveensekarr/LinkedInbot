package com.example.linkedInbot.service.fetch;

import com.example.linkedInbot.model.AppliedJob;
import com.example.linkedInbot.model.JobFetchResult;
import com.example.linkedInbot.service.BotConfig.BotConfigStore;
import com.example.linkedInbot.service.DatabaseStore.JobStoreService;
import com.example.linkedInbot.service.apply.AutoApplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyJobScraperService {

    private final JobFetchService fetchService;
    private final AutoApplyService applyService;
    private final JobStoreService storeService;
    private final BotConfigStore configStore;

    // Ordered by most common first — waitForCards returns whichever one matched
    private static final List<By> CARD_SELECTORS = Arrays.asList(
            By.cssSelector("li[data-occludable-job-id]"),
            By.cssSelector("li.jobs-search-results-list__item"),
            By.xpath("//li[contains(@class, 'job-card-container')]"),
            By.cssSelector(".jobs-search-results__list-item"),
            By.cssSelector(".scaffold-layout__list-item")
    );

    public List<AppliedJob> scrapeAndProcessPages(WebDriver driver, int maxPages) {

        String jobSearchUrl = configStore.get().getSearchUrl();
        if (jobSearchUrl == null || jobSearchUrl.isBlank()) {
            log.error("No search URL configured. Call POST /api/config first.");
            return Collections.emptyList();
        }

        List<AppliedJob> sessionJobs = new ArrayList<>();

        for (int page = 1; page <= maxPages; page++) {
            int startOffset = (page - 1) * 25;
            String currentPageUrl = jobSearchUrl + "&start=" + startOffset;

            log.info("Processing Page {} (URL Offset: {})", page, startOffset);
            driver.get(currentPageUrl);

            try {
                // ── Returns the exact selector that matched, or null if none ──
                By activeSelector = waitForCards(driver);
                if (activeSelector == null) {
                    log.warn("No cards found on Page {} — skipping.", page);
                    continue;
                }

                scrollSidebar(driver);

                // ── Count using the SAME selector that matched the page ────────
                int totalCards = driver.findElements(activeSelector).size();
                log.info("Processing {} cards on page {} using selector: {}", totalCards, page, activeSelector);

                for (int i = 0; i < totalCards; i++) {
                    try {
                        clearAnyLingeringOverlay(driver);

                        // ── Re-fetch fresh list on every card using the active selector ──
                        List<WebElement> freshCards = driver.findElements(activeSelector);
                        if (i >= freshCards.size()) {
                            log.warn("Card index {} out of bounds after re-fetch (list size {}). Stopping page.", i, freshCards.size());
                            break;
                        }
                        WebElement card = freshCards.get(i);

                        WebElement linkElement = card.findElement(
                                By.cssSelector("a.job-card-container__link, a[href*='/jobs/view/']"));
                        String actualJobUrl = linkElement.getAttribute("href").split("\\?")[0];

                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].scrollIntoView({block: 'center'});", card);
                        Thread.sleep(1000);

                        try {
                            card.click();
                        } catch (ElementClickInterceptedException e) {
                            log.warn("Card click intercepted — clearing overlay and retrying with JS click.");
                            clearAnyLingeringOverlay(driver);
                            List<WebElement> retryCards = driver.findElements(activeSelector);
                            if (i < retryCards.size()) {
                                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", retryCards.get(i));
                            }
                        }
                        Thread.sleep(3000);

                        JobFetchResult job = fetchService.fetchJob(driver, actualJobUrl);
                        if (job != null && !"FAILED".equals(job.getJobTitle())) {
                            String finalStatus;
                            if (job.isEasyApply()) {
                                log.info("Easy Apply: {}", job.getJobTitle());
                                boolean applied = applyService.applyForJob(driver, job);
                                finalStatus = applied ? "Applied Successfully" : "Blocked by questions";
                            } else {
                                log.info("Standard Apply: {}", job.getJobTitle());
                                storeService.saveRecord(actualJobUrl, job.getJobTitle(),
                                        job.getCompanyName(), "Standard Apply - Manual");
                                finalStatus = "Standard Apply - Manual";
                            }

                            AppliedJob sessionJob = new AppliedJob();
                            sessionJob.setJobname(job.getJobTitle());
                            sessionJob.setCompanyName(job.getCompanyName());
                            sessionJob.setJobUrl(actualJobUrl);
                            sessionJob.setStatus(finalStatus);
                            sessionJob.setAppliedTime(LocalDateTime.now());
                            sessionJobs.add(sessionJob);
                        }

                        Thread.sleep(10000 + (long) (Math.random() * 10000));

                    } catch (StaleElementReferenceException e) {
                        log.warn("Card {} became stale — skipping to next card.", i);
                        try { clearAnyLingeringOverlay(driver); } catch (Exception ignored) {}

                    } catch (Exception e) {
                        log.warn("Error processing card {}: {}", i, e.getMessage());
                        try { clearAnyLingeringOverlay(driver); } catch (Exception ignored) {}
                    }
                }

            } catch (Exception e) {
                log.error("Critical error on page {}: {}", page, e.getMessage());
            }
        }
        return sessionJobs;
    }

    // ── Returns the By selector that worked, or null if nothing matched ────────
    private By waitForCards(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        for (By selector : CARD_SELECTORS) {
            try {
                wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
                log.info("Cards matched using selector: {}", selector);
                return selector;   // ← return the exact selector, not just true
            } catch (TimeoutException ignored) {}
        }
        // One refresh attempt using first selector
        log.warn("No cards found — refreshing page and retrying...");
        driver.navigate().refresh();
        try {
            Thread.sleep(5000);
            By fallback = CARD_SELECTORS.get(0);
            new WebDriverWait(driver, Duration.ofSeconds(15))
                    .until(ExpectedConditions.visibilityOfElementLocated(fallback));
            log.info("Cards found after refresh using: {}", fallback);
            return fallback;
        } catch (Exception e) {
            return null;   // nothing worked
        }
    }

    // ── Scrolls sidebar to force all cards to load into the DOM ───────────────
    private void scrollSidebar(WebDriver driver) {
        try {
            WebElement sidebar = driver.findElement(By.cssSelector(
                    ".jobs-search-results-list, .jobs-search__results-list, section.scaffold-layout__list"));
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollTop = arguments[0].scrollHeight", sidebar);
            Thread.sleep(2000);
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollTop = 0", sidebar);
        } catch (Exception e) {
            log.warn("Sidebar scroll failed, continuing...");
        }
    }

    // ── Clears any modal/overlay left open after a failed or successful apply ──
    private void clearAnyLingeringOverlay(WebDriver driver) {
        try {
            // TYPE B: Discard confirmation overlay
            List<WebElement> discardOverlays = driver.findElements(By.cssSelector(
                    "[data-test-modal-id='data-test-easy-apply-discard-confirmation']"));
            if (!discardOverlays.isEmpty() && discardOverlays.get(0).isDisplayed()) {
                log.info("Discard confirmation overlay detected — clicking Discard.");
                List<WebElement> discardBtns = discardOverlays.get(0).findElements(
                        By.xpath(".//button[.//span[contains(text(),'Discard')] or contains(text(),'Discard')]"));
                if (!discardBtns.isEmpty()) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", discardBtns.get(0));
                } else {
                    for (WebElement btn : discardOverlays.get(0).findElements(By.tagName("button"))) {
                        if (btn.getText().toLowerCase().contains("discard")) {
                            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
                            break;
                        }
                    }
                }
                Thread.sleep(1000);
                new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(
                                "[data-test-modal-id='data-test-easy-apply-discard-confirmation']")));
                return;
            }

            // TYPE A: "Save this application?" post-submit dialog
            for (WebElement dlg : driver.findElements(
                    By.cssSelector("div.artdeco-modal[role='dialog']"))) {
                if (!dlg.isDisplayed()) continue;
                if (dlg.getText().contains("Save this application")) {
                    log.info("Post-submit 'Save this application?' dialog — clicking Discard.");
                    List<WebElement> btns = dlg.findElements(
                            By.cssSelector("button[data-control-name='discard_application_confirm_btn']"));
                    if (!btns.isEmpty()) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btns.get(0));
                        Thread.sleep(1000);
                    }
                    return;
                }
            }

            // TYPE C: "Application sent" success modal — click Done
            for (WebElement btn : driver.findElements(
                    By.xpath("//button[normalize-space(text())='Done' or @aria-label='Dismiss']"))) {
                if (btn.isDisplayed()) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
                    log.info("Clicked Done on Application sent modal.");
                    Thread.sleep(1000);
                    return;
                }
            }

            // TYPE D: Generic artdeco modal overlay (last resort)
            if (!driver.findElements(By.cssSelector(".artdeco-modal-overlay[aria-hidden='false']")).isEmpty()) {
                log.warn("Unknown modal overlay — pressing ESC.");
                new org.openqa.selenium.interactions.Actions(driver).sendKeys(Keys.ESCAPE).perform();
                Thread.sleep(800);
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("clearAnyLingeringOverlay error (non-fatal): {}", e.getMessage());
        }
    }
}