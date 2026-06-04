package com.example.linkedInbot.service.fetch;

import com.example.linkedInbot.model.JobFetchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobFetchService {

    public JobFetchResult fetchJob(WebDriver driver, String jobUrl){
        try{
            //driver.get(jobUrl);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));

            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            String[] titleSelectors = {
                    "//h1[contains(@class,'t-24')]",
                    "//h1[contains(@class,'job-title')]",
                    "//h1[contains(@class,'jobs-unified-top-card__job-title')]",
                    "//div[contains(@class,'job-details-jobs-unified-top-card__job-title')]//h1",
                    "//div[contains(@class,'jobs-details__main-content')]//h1",
                    "//div[contains(@class,'job-view-layout')]//h1",
                    "//h1"
            };

            String jobTitle = null;
            for(String selector : titleSelectors){
                try{
                    WebElement titleE1 =shortWait.until(
                            ExpectedConditions.visibilityOfElementLocated(By.xpath(selector))
                    );
                    jobTitle = titleE1.getText();
                    if(jobTitle != null && !jobTitle.isBlank()){
                        log.info("Title found with selector: {}",selector);
                        break;
                    }
                } catch(TimeoutException e){
                    log.warn("Title selector failed, trying next; {}",selector);
                }
            }
            if(jobTitle == null || jobTitle.isBlank()){
                throw new RuntimeException("Could not find job title with any known selector");
            }

            String companyName = "UNKNOWN";
            String[] companySelectors = {
                    "//a[contains(@href,'/company/')]",
                    "//span[contains(@class,'topcard__flavor')]",
                    "//div[contains(@class,'job-details-jobs-unified-top-card__company-name')]//a",
                    "//div[contains(@class,'jobs-unified-top-card__company-name')]//a",
                    "//span[contains(@class,'jobs-unified-top-card__subtitle-primary-grouping')]//a"
            };
            for(String selector : companySelectors) {
                try {
                    List<WebElement> elements = driver.findElements(By.xpath(selector));
                    for (WebElement el : elements) {
                        String text = el.getText().trim();
                        if (!text.isBlank()) {
                            companyName = text;
                            log.info("Company found with selector: {}", selector);
                            break;
                        }
                    }
                    if (!"UNKNOWN".equals(companyName)) break;
                } catch (Exception e2) {

                }
            }
            if ("UNKNOWN".equals(companyName)) {
                log.warn("Company name not found for: {}", jobUrl);
            }

            boolean easyApplyAvailable = false;
            try {
                WebElement applyBtn = shortWait.until(
                        ExpectedConditions.presenceOfElementLocated(
                                By.xpath("//button[contains(@class,'jobs-apply-button')]")
                        )
                );
                String btnText = applyBtn.getText().trim();
                easyApplyAvailable = applyBtn.isDisplayed() && applyBtn.isEnabled() && btnText.contains("Easy Apply");
                log.info("Apply button text: {}", btnText);
            } catch (TimeoutException ex) {
                easyApplyAvailable = false;
            }

            log.info("Fetched Job → Title: {}, Company: {}, EasyApply: {}",
                    jobTitle, companyName, easyApplyAvailable);

            return JobFetchResult.builder()
                    .jobTitle(jobTitle)
                    .companyName(companyName)
                    .jobUrl(jobUrl)
                    .easyApply(easyApplyAvailable)
                    .build();

        } catch (Exception e) {
            log.error("Job fetch failed → {}", e.getMessage());

            return JobFetchResult.builder()
                    .jobTitle("FAILED")
                    .companyName("FAILED")
                    .jobUrl(jobUrl)
                    .easyApply(false)
                    .build();

        }
    }
}
